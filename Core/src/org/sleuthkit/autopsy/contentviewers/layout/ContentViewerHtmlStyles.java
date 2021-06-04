/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers.layout;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.awt.Font;
import javax.swing.JTextPane;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

/**
 *
 * @author gregd
 */
public class ContentViewerHtmlStyles {

    // html stylesheet classnames for components
    private static final String CLASS_PREFIX = ContentViewerHtmlStyles.class.getSimpleName();

    private static final String HEADER_CLASSNAME = CLASS_PREFIX + "header";
    private static final String SUB_HEADER_CLASSNAME = CLASS_PREFIX + "subHeader";

    private static final String MESSAGE_CLASSNAME = CLASS_PREFIX + "message";
    private static final String TEXT_CLASSNAME = CLASS_PREFIX + "text";
    private static final String INDENTED_CLASSNAME = CLASS_PREFIX + "indent";
    private static final String SPACED_SECTION_CLASSNAME = CLASS_PREFIX + "spacedSection";
    private static final String KEY_COLUMN_TD_CLASSNAME = CLASS_PREFIX + "keyKolumn";

    private static final Font DEFAULT_FONT = ContentViewerDefaults.getFont();
    private static final Font HEADER_FONT = ContentViewerDefaults.getHeaderFont();
    private static final Font SUB_HEADER_FONT = ContentViewerDefaults.getSubHeaderFont();

    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-family: %s; font-size: %dpt;font-style:italic; margin: 0px; padding: 0px 0px %dpx 0px; } ",
                    MESSAGE_CLASSNAME, DEFAULT_FONT.getFamily(), DEFAULT_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px 0px %dpx 0px;  } ",
                    HEADER_CLASSNAME, HEADER_FONT.getFamily(), HEADER_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px 0px %dpx 0px;  } ",
                    SUB_HEADER_CLASSNAME, SUB_HEADER_FONT.getFamily(), SUB_HEADER_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { font-family: %s; font-size: %dpt; margin: 0px; padding: 0px 0px %dpx 0px;  } ",
                    TEXT_CLASSNAME, DEFAULT_FONT.getFamily(), DEFAULT_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { padding-left: %dpx } ",
                    INDENTED_CLASSNAME, ContentViewerDefaults.getSectionIndent())
            + String.format(" .%s { padding-top: %dpx } ",
                    SPACED_SECTION_CLASSNAME, ContentViewerDefaults.getSectionSpacing())
            + String.format(" .%s { padding-right: %dpx } ",
                    KEY_COLUMN_TD_CLASSNAME, ContentViewerDefaults.getColumnSpacing());

    private static final Supplier<StyleSheet> STYLE_SHEET = Suppliers.memoize(() -> {
        StyleSheet stylesheet = new StyleSheet();
        stylesheet.addRule(STYLE_SHEET_RULE);
        return stylesheet;
    });

    public static String getHeaderClassName() {
        return HEADER_CLASSNAME;
    }

    public static String getSubHeaderClassName() {
        return SUB_HEADER_CLASSNAME;
    }
        
    public static String getMessageClassName() {
        return MESSAGE_CLASSNAME;
    }

    public static String getTextClassName() {
        return TEXT_CLASSNAME;
    }

    public static String getIndentedClassName() {
        return INDENTED_CLASSNAME;
    }

    public static String getSpacedSectionClassName() {
        return SPACED_SECTION_CLASSNAME;
    }

    public static String getKeyColumnClassName() {
        return KEY_COLUMN_TD_CLASSNAME;
    }

    public static String getStyleSheetRule() {
        return STYLE_SHEET_RULE;
    }

    public static StyleSheet getStyleSheet() {
        return STYLE_SHEET.get();
    }

    public static void setupHtmlJTextPane(JTextPane textPane) {
        textPane.setContentType("text/html;charset=UTF-8"); //NON-NLS
        HTMLEditorKit kit = new HTMLEditorKit();
        textPane.setEditorKit(kit);
        kit.setStyleSheet(ContentViewerHtmlStyles.getStyleSheet());
        textPane.setMargin(ContentViewerDefaults.getPanelInsets());
        textPane.setBackground(ContentViewerDefaults.getPanelBackground());
    }
}
