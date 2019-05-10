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
    private final int _bytesPerLine;
    private final ByteBuffer _buf;
    private final JTextComponent _offsetView;
    private final JTextComponent _hexView;
    private final JTextComponent _asciiView;
    private final JLabel _statusLabel;
    private final Color _highlightColor;
    private final DefaultHighlighter.DefaultHighlightPainter _highlighterPainter;
    // these flags are used to ensure we don't end up in a circular event loop where
    //   one component fires an event on the other, who volley's it back.
    private int _hexLastSelectionStart = 0;
    private int _hexLastSelectionEnd = 0;
    private int _asciiLastSelectionStart = 0;
    private int _asciiLastSelectionEnd = 0;

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
        this._buf = buf;
        this._bytesPerLine = bytesPerLine;

        Font font = new Font("Monospaced", Font.PLAIN, 12);

        this._offsetView = new JTextArea();
        this._hexView = new JTextArea();
        this._asciiView = new JTextArea();
        JPanel _statusView = new JPanel();

        // status bar
        _statusView.setBorder(new BevelBorder(BevelBorder.LOWERED));
        this.add(_statusView, BorderLayout.SOUTH);
        _statusView.setPreferredSize(new Dimension(this.getWidth(), 18));
        _statusView.setLayout(new BoxLayout(_statusView, BoxLayout.X_AXIS));
        this._statusLabel = new JLabel("");
        this._statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        _statusView.add(this._statusLabel);

        // right panes are split
        JSplitPane _splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this._hexView, this._asciiView);
        _splitPane.setResizeWeight(0.5);
        _splitPane.setOneTouchExpandable(true);
        _splitPane.setContinuousLayout(true);

        // three panes sitting together
        JPanel panes = new JPanel(new BorderLayout());
        panes.add(this._offsetView, BorderLayout.WEST);
        panes.add(_splitPane, BorderLayout.CENTER);
        JScrollPane scroller = new JScrollPane(panes);
        this.add(scroller, BorderLayout.CENTER);

        _offsetView.setFont(font);
        _hexView.setFont(font);
        _asciiView.setFont(font);

        StringBuilder offsetSB = new StringBuilder();
        StringBuilder hexSB = new StringBuilder();
        StringBuilder asciiSB = new StringBuilder();

        buf.position(0x0);
        for (int i = 0; i < buf.limit(); i++) {
            if (i % this._bytesPerLine == 0x0) {
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

            if (i % this._bytesPerLine == this._bytesPerLine - 1) {
                hexSB.append("\n");
                asciiSB.append("\n");
            }
        }

        this._offsetView.setText(offsetSB.toString());
        this._hexView.setText(hexSB.toString());
        this._asciiView.setText(asciiSB.toString());

        this._hexView.addCaretListener(this);
        this._asciiView.addCaretListener(this);

        this._asciiView.setSelectedTextColor(this._asciiView.getForeground());
        this._hexView.setSelectedTextColor(this._asciiView.getForeground());
        this._highlightColor = this._hexView.getSelectionColor();
        this._highlighterPainter = new DefaultHighlighter.DefaultHighlightPainter(this._highlightColor);
    }

    /**
     * clearHighlight removes any colors applied to the text views.
     */
    private void clearHighlight() {
        this._asciiView.getHighlighter().removeAllHighlights();
        this._hexView.getHighlighter().removeAllHighlights();
    }

    /**
     * setHighlight colors the given byte range.
     *
     * @param startByte The starting byte index of the selection.
     * @param endByte   The ending byte index of the selection.
     */
    private void setHighlight(int startByte, int endByte) {
        int startRows = (startByte - (startByte % this._bytesPerLine)) / this._bytesPerLine;
        int endRows = (endByte - (endByte % this._bytesPerLine)) / this._bytesPerLine;

        this.clearHighlight();

        try {
            this._asciiView.getHighlighter().addHighlight(startByte + startRows, endByte + endRows, this._highlighterPainter);
            this._hexView.getHighlighter().addHighlight((startByte * 3) + startRows, (endByte * 3) + endRows, this._highlighterPainter);
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
    private void setSelection(int startByte, int endByte) {
        this.setHighlight(startByte, endByte);

        if (startByte != endByte) {
            /**
             * @param 1 Start
             * @param 2 End
             * @param 3 Len
             */
            String statusTemplate = "Selection: %1$d to %2$d (len: %3$d) [0x%1$x to 0x%2$x (len: 0x%3$x)]";
            this._statusLabel.setText(String.format(statusTemplate, startByte, endByte, endByte - startByte));
        } else {
            /**
             * @param 1 Start
             */
            String statusTemplate = "Position: %1$d [0x%1$x]";
            this._statusLabel.setText(String.format(statusTemplate, startByte));
        }
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        if (e.getMark() == e.getDot()) {
            this.clearHighlight();
        }

        if (e.getSource() == this._asciiView) {
            int startByte = e.getMark();
            int endByte = e.getDot();

            if (startByte > endByte) {
                int t = endByte;
                endByte = startByte;
                startByte = t;
            }

            // the number of line endings before the start,end points
            int startRows = (startByte - (startByte % this._bytesPerLine)) / this._bytesPerLine;
            int endRows = (endByte - (endByte % this._bytesPerLine)) / this._bytesPerLine;

            // the byte index of the start,end points in the ASCII view
            startByte -= startRows;
            endByte -= endRows;

            // avoid the loop
            if (_asciiLastSelectionStart == startByte && _asciiLastSelectionEnd == endByte) {
                return;
            }
            _asciiLastSelectionStart = startByte;
            _asciiLastSelectionEnd = endByte;

            this.setSelection(startByte, endByte);
        } else if (e.getSource() == this._hexView) {
            int startByte = e.getMark();
            int endByte = e.getDot();

            if (startByte > endByte) {
                int t = endByte;
                endByte = startByte;
                startByte = t;
            }

            // the number of line endings before the start,end points
            int startRows = (startByte - (startByte % this._bytesPerLine)) / (3 * this._bytesPerLine);
            int endRows = (endByte - (endByte % this._bytesPerLine)) / (3 * this._bytesPerLine);

            // the byte index of the start,end points in the ASCII view
            startByte -= startRows;
            startByte /= 3;
            endByte -= endRows;
            endByte /= 3;

            if (_hexLastSelectionStart == startByte && _hexLastSelectionEnd == endByte) {
                return;
            }
            _hexLastSelectionStart = startByte;
            _hexLastSelectionEnd = endByte;

            this.setSelection(startByte, endByte);
        } else {
            logger.log(Level.INFO, "from unknown");
        }
    }
}
