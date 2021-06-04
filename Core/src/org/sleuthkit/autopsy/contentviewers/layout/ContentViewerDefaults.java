/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers.layout;

import com.google.common.base.Suppliers;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import com.google.common.base.Supplier;
import java.awt.Color;
import javax.swing.UIManager;

/**
 *
 * @author gregd
 */
public class ContentViewerDefaults {

    private static final Supplier<Font> DEFAULT_FONT = Suppliers.memoize(() -> UIManager.getDefaults().getFont("Label.font"));

    private static final Supplier<Integer> DEFAULT_FONT_PX = Suppliers.memoize(() -> {
        // based on https://stackoverflow.com/questions/5829703/java-getting-a-font-with-a-specific-height-in-pixels/26564924#26564924
        return (int) Math.round(DEFAULT_FONT.get().getSize() * Toolkit.getDefaultToolkit().getScreenResolution() / 72.0);
    });
    
    private static final Supplier<Font> SUB_HEADER_FONT = Suppliers.memoize(() -> {
        Font defaultFont = DEFAULT_FONT.get();
        return defaultFont.deriveFont(Font.BOLD);
    });

    private static final Supplier<Font> HEADER_FONT = Suppliers.memoize(() -> {
        Font defaultFont = DEFAULT_FONT.get();
        return defaultFont.deriveFont(Font.BOLD, defaultFont.getSize() + 2);
    });

    private static final Supplier<Insets> DEFAULT_PANEL_INSETS = Suppliers.memoize(() -> UIManager.getDefaults().getInsets("TextPane.margin"));

    private static final Supplier<Integer> DEFAULT_INDENT = Suppliers.memoize(() -> DEFAULT_FONT_PX.get());
    private static final Supplier<Integer> DEFAULT_SECTION_SPACING = Suppliers.memoize(() -> DEFAULT_FONT_PX.get());

    private static final Supplier<Integer> DEFAULT_COLUMN_SPACING = Suppliers.memoize(() -> (DEFAULT_FONT_PX.get() / 3));
    private static final Supplier<Integer> DEFAULT_LINE_SPACING = Suppliers.memoize(() -> (DEFAULT_FONT_PX.get() / 5));

    private static final Supplier<Color> DEFAULT_BACKGROUND = Suppliers.memoize(() -> UIManager.getColor("Panel.background"));

    public static int getColumnSpacing() {
        return DEFAULT_COLUMN_SPACING.get();
    }

    public static Font getFont() {
        return DEFAULT_FONT.get();
    }

    public static Font getHeaderFont() {
        return HEADER_FONT.get();
    }

    public static Insets getPanelInsets() {
        return DEFAULT_PANEL_INSETS.get();
    }

    public static Integer getSectionIndent() {
        return DEFAULT_INDENT.get();
    }

    public static Integer getSectionSpacing() {
        return DEFAULT_SECTION_SPACING.get();
    }

    public static Integer getLineSpacing() {
        return DEFAULT_LINE_SPACING.get();
    }

    public static Color getPanelBackground() {
        return DEFAULT_BACKGROUND.get();
    }
    
    public static Font getSubHeaderFont() {
        return SUB_HEADER_FONT.get();
    }
}
