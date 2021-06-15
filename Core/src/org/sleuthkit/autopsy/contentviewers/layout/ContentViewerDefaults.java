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
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Color;
import javax.swing.UIManager;

/**
 * Default values for layout of content values.
 */
public class ContentViewerDefaults {

    private static final Font DEFAULT_FONT = UIManager.getDefaults().getFont("Label.font");

    // based on https://stackoverflow.com/questions/5829703/java-getting-a-font-with-a-specific-height-in-pixels/26564924#26564924
    private static final Double PT_TO_PX = Toolkit.getDefaultToolkit().getScreenResolution() / 72.0;

    private static final int DEFAULT_FONT_PX = (int) Math.round(DEFAULT_FONT.getSize() * PT_TO_PX);

    private static final Font SUB_HEADER_FONT = DEFAULT_FONT.deriveFont(Font.BOLD);

    private static final Font HEADER_FONT = DEFAULT_FONT.deriveFont(Font.BOLD, DEFAULT_FONT.getSize() + 2);

    private static final Font MESSAGE_FONT = DEFAULT_FONT.deriveFont(Font.ITALIC);
    
    private static final Font MONOSPACED_FONT = new Font(Font.MONOSPACED, Font.PLAIN, DEFAULT_FONT.getSize());

    private static final Insets DEFAULT_PANEL_INSETS = UIManager.getDefaults().getInsets("TextPane.margin");

    private static final int DEFAULT_INDENT = DEFAULT_FONT_PX;
    private static final int DEFAULT_SECTION_SPACING = DEFAULT_FONT_PX;

    private static final int DEFAULT_COLUMN_SPACING = (int) Math.round((double) DEFAULT_FONT_PX / 3);

    private static final int DEFAULT_LINE_SPACING = (int) Math.round((double) DEFAULT_FONT_PX / 5);

    private static final Color DEFAULT_BACKGROUND = UIManager.getColor("Panel.background");

    /**
     * Returns the horizontal spacing between columns in a table in pixels.
     *
     * @return The horizontal spacing between columns in a table in pixels.
     */
    public static int getColumnSpacing() {
        return DEFAULT_COLUMN_SPACING;
    }

    /**
     * Returns the default font to be used.
     *
     * @return the default font to be used.
     */
    public static Font getFont() {
        return DEFAULT_FONT;
    }

    /**
     * Returns the font to be displayed for messages.
     *
     * @return The font to be displayed for messages.
     */
    public static Font getMessageFont() {
        return MESSAGE_FONT;
    }

    /**
     * Returns the font to be displayed for messages.
     *
     * @return The font to be displayed for messages.
     */
    public static Font getHeaderFont() {
        return HEADER_FONT;
    }

    /**
     * Returns the font to be displayed for sub headers.
     *
     * @return The font to be displayed for sub headers.
     */
    public static Font getSubHeaderFont() {
        return SUB_HEADER_FONT;
    }

    /**
     * Returns the font to be used for normal monospace.
     *
     * @return The font to be used for normal monospace.
     */    
    public static Font getMonospacedFont() {
        return MONOSPACED_FONT;
    }

    /**
     * Returns the insets of the content within the parent content viewer panel.
     *
     * @return The insets of the content within the parent content viewer panel.
     */
    public static Insets getPanelInsets() {
        return DEFAULT_PANEL_INSETS;
    }

    /**
     * Returns the size in pixels that sections should be indented.
     *
     * @return The size in pixels that sections should be indented.
     */
    public static Integer getSectionIndent() {
        return DEFAULT_INDENT;
    }

    /**
     * Returns the spacing between sections in pixels.
     *
     * @return The spacing between sections in pixels.
     */
    public static Integer getSectionSpacing() {
        return DEFAULT_SECTION_SPACING;
    }

    /**
     * Returns the spacing between lines of text in pixels.
     *
     * @return The spacing between lines of text in pixels.
     */
    public static Integer getLineSpacing() {
        return DEFAULT_LINE_SPACING;
    }

    /**
     * Returns the color to be used as the background of the panel.
     *
     * @return The color to be used as the background of the panel.
     */
    public static Color getPanelBackground() {
        return DEFAULT_BACKGROUND;
    }

    /**
     * Returns the ratio of point size to pixel size for the user's screen
     * resolution.
     *
     * @return The ratio of point size to pixel size for the user's screen
     *         resolution.
     */
    public static Double getPtToPx() {
        return PT_TO_PX;
    }
}
