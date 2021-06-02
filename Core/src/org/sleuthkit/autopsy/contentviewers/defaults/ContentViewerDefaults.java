/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.contentviewers.defaults;

import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.function.Supplier;
import javax.swing.UIManager;

/**
 *
 * @author gregd
 */
public class ContentViewerDefaults {

    private static class Cacheable<T> {

        private final Supplier<T> itemProvider;
        private T item = null;

        Cacheable(Supplier<T> itemProvider) {
            this.itemProvider = itemProvider;
        }

        T get() {
            if (item == null) {
                item = itemProvider.get();
            }

            return item;
        }
    }

    private static final Cacheable<Font> DEFAULT_FONT = new Cacheable<>(() -> UIManager.getDefaults().getFont("Label.font"));
    
    private static final Cacheable<Integer> DEFAULT_FONT_PX = new Cacheable<>(() -> {
        // based on https://stackoverflow.com/questions/5829703/java-getting-a-font-with-a-specific-height-in-pixels/26564924#26564924
        return (int) Math.round(DEFAULT_FONT.get().getSize() * Toolkit.getDefaultToolkit().getScreenResolution() / 72.0);
    });
    
    private static final Cacheable<Font> HEADER_FONT = new Cacheable<>(() -> {
        Font defaultFont = DEFAULT_FONT.get();
        return defaultFont.deriveFont(Font.BOLD, defaultFont.getSize() + 2);
    });
        
    private static final Cacheable<Insets> DEFAULT_PANEL_INSETS = new Cacheable<>(() -> UIManager.getDefaults().getInsets("TextPane.margin"));
    
    private static final Cacheable<Integer> DEFAULT_INDENT = new Cacheable<>(() -> DEFAULT_FONT_PX.get());
    private static final Cacheable<Integer> DEFAULT_SECTION_SPACING = new Cacheable<>(() -> DEFAULT_FONT_PX.get());
    

    
    
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
    
    // line spacing???
}
