/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.rejview;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.border.BevelBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * HexView is a standard three-paned hex editor widget that displays binary
 * data.
 *
 * Note, this does not do any intelligent paging of the data. You should
 * estimate it to load three strings with length equal to the given ByteBuffer.
 * So its probably not good to use this view with large files.
 */
final class HexView extends JPanel {

    private final static int DEFAULT_BYTES_PER_LINE = 0x10;
    private final static char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private final static int CHAR_ARRAY_SIZE = 3;
    private static final Logger logger = Logger.getLogger(HexView.class.getName());
    private static final long serialVersionUID = 1L;
    private final int bytesPerLine;
    private final HexViewListener hexViewListener = new HexViewListener();
    private final JTextComponent hexViewTextArea;
    private final JTextComponent asciiViewTextArea;
    private final JLabel statusLabel;
    private final DefaultHighlighter.DefaultHighlightPainter highlighterPainter;
    // these flags are used to ensure we don't end up in a circular event loop where
    //   one component fires an event on the other, who volley's it back.
    private int hexLastSelectionStart = 0;
    private int hexLastSelectionEnd = 0;
    private int asciiLastSelectionStart = 0;
    private int asciiLastSelectionEnd = 0;

    /**
     * Uses the default 0x10 bytes per line.
     *
     * @param buf The binary data to display within this hex view.
     */
    HexView(ByteBuffer buf) {
        this(buf, DEFAULT_BYTES_PER_LINE);
    }

    /**
     * @param buf          The binary data to display within this hex view.
     * @param bytesPerLine The number of bytes to display per line.
     */
    HexView(ByteBuffer buf, int bytesPerLine) {
        super(new BorderLayout());
        this.bytesPerLine = bytesPerLine;

        Font font = new Font("Monospaced", Font.PLAIN, 12);  //Non-NLS
        //Font should be left alone as we want to ensure a monospaced font is used 
        //when displaying Hex, instead of the default font.

        JTextComponent offsetView = new JTextArea();
        this.hexViewTextArea = new JTextArea();
        this.asciiViewTextArea = new JTextArea();
        JPanel statusView = new JPanel();

        // status bar
        statusView.setBorder(new BevelBorder(BevelBorder.LOWERED));
        this.add(statusView, BorderLayout.SOUTH);
        statusView.setPreferredSize(new Dimension(this.getWidth(), 18));
        statusView.setLayout(new BoxLayout(statusView, BoxLayout.X_AXIS));
        this.statusLabel = new JLabel("");
        this.statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusView.add(this.statusLabel);

        // right panes are split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.hexViewTextArea, this.asciiViewTextArea);
        splitPane.setResizeWeight(0.5);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        // three panes sitting together
        JPanel panes = new JPanel(new BorderLayout());
        panes.add(offsetView, BorderLayout.WEST);
        panes.add(splitPane, BorderLayout.CENTER);
        JScrollPane scroller = new JScrollPane(panes);
        this.add(scroller, BorderLayout.CENTER);

        offsetView.setFont(font);
        hexViewTextArea.setFont(font);
        asciiViewTextArea.setFont(font);

        StringBuilder offsetSB = new StringBuilder();
        StringBuilder hexSB = new StringBuilder();
        StringBuilder asciiSB = new StringBuilder();

        buf.position(0x0);
        for (int i = 0; i < buf.limit(); i++) {
            if (i % this.bytesPerLine == 0x0) {
                offsetSB.append(String.format("0x%x  \n", i));
            }

            byte b = buf.get();
            char[] hex = new char[CHAR_ARRAY_SIZE];
            hex[0] = HEX_DIGITS[(b >>> 4) & 0x0F];
            hex[1] = HEX_DIGITS[b & 0x0F];
            hex[2] = ' ';
            hexSB.append(hex);

            if (b >= ' ' && b <= '~') {
                asciiSB.append((char) b);
            } else {
                asciiSB.append('.');
            }

            if (i % this.bytesPerLine == this.bytesPerLine - 1) {
                hexSB.append('\n');
                asciiSB.append('\n');
            }
        }

        offsetView.setText(offsetSB.toString());
        this.hexViewTextArea.setText(hexSB.toString());
        this.asciiViewTextArea.setText(asciiSB.toString());
        this.hexViewTextArea.addCaretListener(hexViewListener);
        this.asciiViewTextArea.addCaretListener(hexViewListener);
        this.asciiViewTextArea.setSelectedTextColor(this.asciiViewTextArea.getForeground());
        this.hexViewTextArea.setSelectedTextColor(this.asciiViewTextArea.getForeground());
        this.highlighterPainter = new DefaultHighlighter.DefaultHighlightPainter(this.hexViewTextArea.getSelectionColor());
    }

    /**
     * Private listener class to listen make changes based on events in the
     * asciiViewTextArea component and the hexViewTextArea
     */
    private class HexViewListener implements CaretListener {

        @Override
        public void caretUpdate(CaretEvent e) {
            if (e.getMark() == e.getDot()) {
                this.clearHighlight();
            }

            if (e.getSource() == asciiViewTextArea) {
                int startByte = e.getMark();
                int endByte = e.getDot();

                if (startByte > endByte) {
                    int t = endByte;
                    endByte = startByte;
                    startByte = t;
                }

                // the number of line endings before the start,end points
                int startRows = (startByte - (startByte % bytesPerLine)) / bytesPerLine;
                int endRows = (endByte - (endByte % bytesPerLine)) / bytesPerLine;

                // the byte index of the start,end points in the ASCII view
                startByte -= startRows;
                endByte -= endRows;

                // avoid the loop
                if (asciiLastSelectionStart == startByte && asciiLastSelectionEnd == endByte) {
                    return;
                }
                asciiLastSelectionStart = startByte;
                asciiLastSelectionEnd = endByte;

                this.setSelection(startByte, endByte);
            } else if (e.getSource() == hexViewTextArea) {
                int startByte = e.getMark();
                int endByte = e.getDot();

                if (startByte > endByte) {
                    int t = endByte;
                    endByte = startByte;
                    startByte = t;
                }

                // the number of line endings before the start,end points
                int startRows = (startByte - (startByte % bytesPerLine)) / (CHAR_ARRAY_SIZE * bytesPerLine);
                int endRows = (endByte - (endByte % bytesPerLine)) / (CHAR_ARRAY_SIZE * bytesPerLine);

                // the byte index of the start,end points in the ASCII view
                startByte -= startRows;
                startByte /= CHAR_ARRAY_SIZE;
                endByte -= endRows;
                endByte /= CHAR_ARRAY_SIZE;

                if (hexLastSelectionStart == startByte && hexLastSelectionEnd == endByte) {
                    return;
                }
                hexLastSelectionStart = startByte;
                hexLastSelectionEnd = endByte;

                this.setSelection(startByte, endByte);
            } else {
                logger.log(Level.INFO, "Source of event was neither the ascii view or the hex view text area");
            }
        }

        /**
         * setSelection sets the given byte range as "selected", which from a
         * GUI perspective means the bytes are highlighted, and the status bar
         * updated.
         *
         * @param startByte The starting byte index of the selection.
         * @param endByte   The ending byte index of the selection.
         */
        @Messages({"# {0} - startByteD",
            "# {1} - endByteD",
            "# {2} - lengthD",
            "# {3} - startByteH",
            "# {4} - endByteH",
            "# {5} - lengthH",
            "HexView.statusTemplate.nonZeroLength=Selection: {0} to {1} (len: {2}) [{3} to {4} (len: {5})",
            "# {0} - startByteDec",
            "# {1} - startByteHex",
            "HexView.statusTemplate.zeroLength=Position: {0} [{1}])"})
        private void setSelection(int startByte, int endByte) {
            this.setHighlight(startByte, endByte);

            if (startByte != endByte) {
                /*
                 * param 1 Start
                 * param 2 End
                 * param 3 Len
                 */
                int length = endByte - startByte;
                String text = Bundle.HexView_statusTemplate_nonZeroLength(
                        startByte,
                        endByte,
                        length,
                        String.format("0x%1$x", startByte),
                        String.format("0x%1$x", endByte),
                        String.format("0x%1$x", length));
                statusLabel.setText(text);
            } else {
                /*
                 * param 1 Start
                 */
                String text = Bundle.HexView_statusTemplate_zeroLength(startByte, String.format("0x%1$x", startByte));
                statusLabel.setText(text);
            }
        }

        /**
         * clearHighlight removes any colors applied to the text views.
         */
        private void clearHighlight() {
            asciiViewTextArea.getHighlighter().removeAllHighlights();
            hexViewTextArea.getHighlighter().removeAllHighlights();
        }

        /**
         * setHighlight colors the given byte range.
         *
         * @param startByte The starting byte index of the selection.
         * @param endByte   The ending byte index of the selection.
         */
        private void setHighlight(int startByte, int endByte) {
            int startRows = (startByte - (startByte % bytesPerLine)) / bytesPerLine;
            int endRows = (endByte - (endByte % bytesPerLine)) / bytesPerLine;

            this.clearHighlight();

            try {
                asciiViewTextArea.getHighlighter().addHighlight(startByte + startRows, endByte + endRows, highlighterPainter);
                hexViewTextArea.getHighlighter().addHighlight((startByte * CHAR_ARRAY_SIZE) + startRows, (endByte * CHAR_ARRAY_SIZE) + endRows, highlighterPainter);
            } catch (BadLocationException ex) {
                logger.log(Level.WARNING, "Invalid highlighting location specified", ex);
            }
        }
    }
}
