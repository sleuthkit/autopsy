/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.contentviewers.layout;

import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.text.EditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 * The style sheet an class names to be used with content viewers using html
 * rendering.
 */
public class ContentViewerHtmlStyles {

    // html stylesheet classnames for components
    private static final String CLASS_PREFIX = ContentViewerHtmlStyles.class.getSimpleName();

    private static final String HEADER_CLASSNAME = CLASS_PREFIX + "header";
    private static final String SUB_HEADER_CLASSNAME = CLASS_PREFIX + "subHeader";

    private static final String MESSAGE_CLASSNAME = CLASS_PREFIX + "message";
    private static final String TEXT_CLASSNAME = CLASS_PREFIX + "text";
    private static final String MONOSPACED_CLASSNAME = CLASS_PREFIX + "monospaced";
    private static final String INDENTED_CLASSNAME = CLASS_PREFIX + "indent";
    private static final String SPACED_SECTION_CLASSNAME = CLASS_PREFIX + "spacedSection";
    private static final String KEY_COLUMN_TD_CLASSNAME = CLASS_PREFIX + "keyKolumn";

    private static final Font DEFAULT_FONT = ContentViewerDefaults.getFont();
    private static final Font MESSAGE_FONT = ContentViewerDefaults.getMessageFont();
    private static final Font HEADER_FONT = ContentViewerDefaults.getHeaderFont();
    private static final Font SUB_HEADER_FONT = ContentViewerDefaults.getSubHeaderFont();
    private static final Font MONOSPACED_FONT = ContentViewerDefaults.getMonospacedFont();

    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-family: %s; font-size: %dpt;font-style:italic; margin: 0px; padding: 0px 0px %dpt 0px; } ",
                    MESSAGE_CLASSNAME, MESSAGE_FONT.getFamily(), MESSAGE_FONT.getSize(), pxToPt(ContentViewerDefaults.getLineSpacing()))
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px 0px %dpt 0px;  } ",
                    HEADER_CLASSNAME, HEADER_FONT.getFamily(), HEADER_FONT.getSize(), pxToPt(ContentViewerDefaults.getLineSpacing()))
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px 0px %dpt 0px;  } ",
                    SUB_HEADER_CLASSNAME, SUB_HEADER_FONT.getFamily(), SUB_HEADER_FONT.getSize(), pxToPt(ContentViewerDefaults.getLineSpacing()))
            + String.format(" .%s { font-family: %s; font-size: %dpt; margin: 0px; padding: 0px 0px %dpt 0px;  } ",
                    TEXT_CLASSNAME, DEFAULT_FONT.getFamily(), DEFAULT_FONT.getSize(), pxToPt(ContentViewerDefaults.getLineSpacing()))
            + String.format(" .%s { font-family: %s; font-size: %dpt; margin: 0px; padding: 0px 0px %dpt 0px;  } ",
                    MONOSPACED_CLASSNAME, Font.MONOSPACED, MONOSPACED_FONT.getSize(), pxToPt(ContentViewerDefaults.getLineSpacing()))
            + String.format(" .%s { padding-left: %dpt } ",
                    INDENTED_CLASSNAME, pxToPt(ContentViewerDefaults.getSectionIndent()))
            + String.format(" .%s { padding-top: %dpt } ",
                    SPACED_SECTION_CLASSNAME, pxToPt(ContentViewerDefaults.getSectionSpacing()))
            + String.format(" .%s { padding-right: %dpt; white-space: nowrap; } ",
                    KEY_COLUMN_TD_CLASSNAME, pxToPt(ContentViewerDefaults.getColumnSpacing()));

    private static final StyleSheet STYLE_SHEET = new StyleSheet();

    static {
        // add the style rule to the style sheet.
        STYLE_SHEET.addRule(STYLE_SHEET_RULE);
    }

    /**
     * Converts pixel size to point size. The html rendering seems more
     * consistent with point size versus pixel size.
     *
     * @param px The pixel size.
     *
     * @return The point size.
     */
    private static int pxToPt(int px) {
        return (int) Math.round(((double) px) / ContentViewerDefaults.getPtToPx());
    }

    /**
     * Returns the class name to use for header text.
     *
     * @return The class name to use for header text.
     */
    public static String getHeaderClassName() {
        return HEADER_CLASSNAME;
    }

    /**
     * Returns the class name to use for sub header text.
     *
     * @return The class name to use for sub header text.
     */
    public static String getSubHeaderClassName() {
        return SUB_HEADER_CLASSNAME;
    }

    /**
     * Returns the class name to use for message text.
     *
     * @return The class name to use for message text.
     */
    public static String getMessageClassName() {
        return MESSAGE_CLASSNAME;
    }

    /**
     * Returns the class name to use for regular text.
     *
     * @return The class name to use for regular text.
     */
    public static String getTextClassName() {
        return TEXT_CLASSNAME;
    }

    /**
     * Returns the class name to use for monospaced text.
     *
     * @return The class name to use for monospaced text.
     */
    public static String getMonospacedClassName() {
        return MONOSPACED_CLASSNAME;
    }

    /**
     * Returns the class name to use for an indented (left padding) section.
     *
     * @return The class name to use for an indented (left padding) section.
     */
    public static String getIndentedClassName() {
        return INDENTED_CLASSNAME;
    }

    /**
     * Returns the class name to use for a section with spacing (top padding)
     * section.
     *
     * @return The class name to use for a section with spacing (top padding)
     *         section.
     */
    public static String getSpacedSectionClassName() {
        return SPACED_SECTION_CLASSNAME;
    }

    /**
     * Returns the class name to use for a key column with right spacing (right
     * padding).
     *
     * @return The class name to use for a key column with right spacing (right
     *         padding).
     */
    public static String getKeyColumnClassName() {
        return KEY_COLUMN_TD_CLASSNAME;
    }

    /**
     * If the textPane has an HTMLEditorKit, specifies the
     * ContentViewerHTMLStyles styles to use refreshing the styles.
     *
     * @param textPane The text pane.
     */
    public static void setStyles(JTextPane textPane) {
        EditorKit editorKit = textPane.getEditorKit();
        if (editorKit instanceof HTMLEditorKit) {
            ((HTMLEditorKit) editorKit).setStyleSheet(STYLE_SHEET);
        }
    }

    /**
     * Sets up a JTextPane for html rendering using the css class names
     * specified in this class.
     *
     * @param textPane The JTextPane to set up for content viewer html
     *                 rendering.
     */
    public static void setupHtmlJTextPane(JTextPane textPane) {
        textPane.setContentType("text/html;charset=UTF-8"); //NON-NLS
        HTMLEditorKit kit = new HTMLEditorKit();
        textPane.setEditorKit(kit);
        kit.setStyleSheet(STYLE_SHEET);
        textPane.setMargin(ContentViewerDefaults.getPanelInsets());
        textPane.setBackground(ContentViewerDefaults.getPanelBackground());
    }
}
