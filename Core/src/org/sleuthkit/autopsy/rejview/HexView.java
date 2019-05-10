/*
 * Autopsy Forensic Browser
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
import java.awt.Color;
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
final class HexView extends JPanel implements CaretListener {

    private final static int DEFAULT_BYTES_PER_LINE = 0x10;
    private final static char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private static final Logger logger = Logger.getLogger(HexView.class.getName());
    private static final long serialVersionUID = 1L;
    private final int bytesPerLine;
    private final ByteBuffer buf;
    private final JTextComponent offsetView;
    private final JTextComponent hexView;
    private final JTextComponent asciiView;
    private final JLabel statusLabel;
    private final Color highlightColor;
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
        this.buf = buf;
        this.bytesPerLine = bytesPerLine;

        Font font = new Font("Monospaced", Font.PLAIN, 12);  //Non-NLS

        this.offsetView = new JTextArea();
        this.hexView = new JTextArea();
        this.asciiView = new JTextArea();
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
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.hexView, this.asciiView);
        splitPane.setResizeWeight(0.5);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        // three panes sitting together
        JPanel panes = new JPanel(new BorderLayout());
        panes.add(this.offsetView, BorderLayout.WEST);
        panes.add(splitPane, BorderLayout.CENTER);
        JScrollPane scroller = new JScrollPane(panes);
        this.add(scroller, BorderLayout.CENTER);

        offsetView.setFont(font);
        hexView.setFont(font);
        asciiView.setFont(font);

        StringBuilder offsetSB = new StringBuilder();
        StringBuilder hexSB = new StringBuilder();
        StringBuilder asciiSB = new StringBuilder();

        buf.position(0x0);
        for (int i = 0; i < buf.limit(); i++) {
            if (i % this.bytesPerLine == 0x0) {
                offsetSB.append(String.format("0x%x  \n", i));
            }

            byte b = buf.get();
            char[] hex = new char[3];
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
                hexSB.append("\n");
                asciiSB.append("\n");
            }
        }

        this.offsetView.setText(offsetSB.toString());
        this.hexView.setText(hexSB.toString());
        this.asciiView.setText(asciiSB.toString());
        this.hexView.addCaretListener(this);
        this.asciiView.addCaretListener(this);
        this.asciiView.setSelectedTextColor(this.asciiView.getForeground());
        this.hexView.setSelectedTextColor(this.asciiView.getForeground());
        this.highlightColor = this.hexView.getSelectionColor();
        this.highlighterPainter = new DefaultHighlighter.DefaultHighlightPainter(this.highlightColor);
    }

    /**
     * clearHighlight removes any colors applied to the text views.
     */
    private void clearHighlight() {
        this.asciiView.getHighlighter().removeAllHighlights();
        this.hexView.getHighlighter().removeAllHighlights();
    }

    /**
     * setHighlight colors the given byte range.
     *
     * @param startByte The starting byte index of the selection.
     * @param endByte   The ending byte index of the selection.
     */
    private void setHighlight(int startByte, int endByte) {
        int startRows = (startByte - (startByte % this.bytesPerLine)) / this.bytesPerLine;
        int endRows = (endByte - (endByte % this.bytesPerLine)) / this.bytesPerLine;

        this.clearHighlight();

        try {
            this.asciiView.getHighlighter().addHighlight(startByte + startRows, endByte + endRows, this.highlighterPainter);
            this.hexView.getHighlighter().addHighlight((startByte * 3) + startRows, (endByte * 3) + endRows, this.highlighterPainter);
        } catch (BadLocationException ex) {
            logger.log(Level.WARNING, "bad location", ex);
        }
    }

    /**
     * setSelection sets the given byte range as "selected", which from a GUI
     * perspective means the bytes are highlighted, and the status bar updated.
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
            /**
             * @param 1 Start
             * @param 2 End
             * @param 3 Len
             */
            int length = endByte - startByte;
            String text = Bundle.HexView_statusTemplate_nonZeroLength(
                    startByte,
                    endByte,
                    length,
                    String.format("0x%1$x", startByte),
                    String.format("0x%1$x", endByte),
                    String.format("0x%1$x", length));
            this.statusLabel.setText(text);
        } else {
            /**
             * @param 1 Start
             */
            String text = Bundle.HexView_statusTemplate_zeroLength(startByte, String.format("0x%1$x", startByte));
            this.statusLabel.setText(text);
        }
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        if (e.getMark() == e.getDot()) {
            this.clearHighlight();
        }

        if (e.getSource() == this.asciiView) {
            int startByte = e.getMark();
            int endByte = e.getDot();

            if (startByte > endByte) {
                int t = endByte;
                endByte = startByte;
                startByte = t;
            }

            // the number of line endings before the start,end points
            int startRows = (startByte - (startByte % this.bytesPerLine)) / this.bytesPerLine;
            int endRows = (endByte - (endByte % this.bytesPerLine)) / this.bytesPerLine;

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
        } else if (e.getSource() == this.hexView) {
            int startByte = e.getMark();
            int endByte = e.getDot();

            if (startByte > endByte) {
                int t = endByte;
                endByte = startByte;
                startByte = t;
            }

            // the number of line endings before the start,end points
            int startRows = (startByte - (startByte % this.bytesPerLine)) / (3 * this.bytesPerLine);
            int endRows = (endByte - (endByte % this.bytesPerLine)) / (3 * this.bytesPerLine);

            // the byte index of the start,end points in the ASCII view
            startByte -= startRows;
            startByte /= 3;
            endByte -= endRows;
            endByte /= 3;

            if (hexLastSelectionStart == startByte && hexLastSelectionEnd == endByte) {
                return;
            }
            hexLastSelectionStart = startByte;
            hexLastSelectionEnd = endByte;

            this.setSelection(startByte, endByte);
        } else {
            logger.log(Level.INFO, "from unknown");
        }
    }
}
