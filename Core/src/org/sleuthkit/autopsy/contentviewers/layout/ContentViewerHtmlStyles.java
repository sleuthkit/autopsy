/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JLabel;

/**
 *
 * @author gregd
 */
public class ContentViewerHtmlStyles {
    
    private static final String DEFAULT_FONT_FAMILY = new JLabel().getFont().getFamily();
    private static final int DEFAULT_FONT_SIZE = new JLabel().getFont().getSize();
    private static final Color DEFAULT_BACKGROUND = new JLabel().getBackground();

    // html stylesheet classnames for components
    private static final String ANALYSIS_RESULTS_CLASS_PREFIX = "analysisResult_";
    private static final String SPACED_SECTION_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "spacedSection";
    private static final String SUBSECTION_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "subsection";
    private static final String HEADER_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "header";
    public static final String MESSAGE_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "message";
    public static final String TD_CLASSNAME = ANALYSIS_RESULTS_CLASS_PREFIX + "td";

    // Anchors are inserted into the navigation so that the viewer can navigate to a selection.  
    // This is the prefix of those anchors.
    private static final String CLASSNAME_PREFIX = "ContentViewer_";

    // how big the header should be
    private static final int HEADER_FONT_SIZE = DEFAULT_FONT_SIZE + 2;

    // spacing occurring after an item
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_SIZE / 2;
    private static final int CELL_SPACING = DEFAULT_FONT_SIZE / 2;

    // the subsection indent
    private static final int DEFAULT_SUBSECTION_LEFT_PAD = DEFAULT_FONT_SIZE;

    
    
    
    private static final Font DEFAULT_FONT = ContentViewerDefaults.getFont();
    private static final Font HEADER_FONT = ContentViewerDefaults.getHeaderFont();
    
    
    // additional styling for components
    private static final String STYLE_SHEET_RULE
            = String.format(" .%s { font-family: %s; font-size: %dpt;font-style:italic; margin: 0px; padding: 0px 0px %dpx 0px; } ", 
                    MESSAGE_CLASSNAME, DEFAULT_FONT.getFamily(), DEFAULT_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { font-family: %s; font-size: %dpt; font-weight: bold; margin: 0px; padding: 0px 0px %dpx 0px;  } ",
                    HEADER_CLASSNAME, HEADER_FONT.getFamily(), HEADER_FONT.getSize(), ContentViewerDefaults.getLineSpacing())
            + String.format(" .%s { font-family: %s; font-size: %dpt; margin: 0px; padding: 0px 0px %dpx 0px;  } ",
                    TEXT_CLASSNAME, DEFAULT_FONT.getFamily(), DEFAULT_FONT.getSize(), ContentViewerDefaults.getLineSpacing())

            + String.format(" .%s { padding-left: %dpx } ",
                    INDENTED_CLASSNAME, ContentViewerDefaults.getSectionIndent())
            + String.format(" .%s { margin-top: %dpx } ",
                    NOT_FIRST_SECTION_CLASSNAME, ContentViewerDefaults.getSectionSpacing())
            + String.format(" .%s { margin-top: %dpx } ",
                    KEY_COLUMN_TD_CLASSNAME, ContentViewerDefaults.getColumnSpacing());

}
