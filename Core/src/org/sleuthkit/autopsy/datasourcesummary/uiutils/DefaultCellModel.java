/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author gregd
 */
/**
 * The default cell model.
 */
public class DefaultCellModel<T> implements GuiCellModel, ExcelCellModel {
    private final T data;
    private final Function<T, String> stringConverter;    
    String tooltip;
    CellModel.HorizontalAlign horizontalAlignment;
    Insets insets;
    List<MenuItem> popupMenu;
    Supplier<List<MenuItem>> menuItemSupplier;
    private final String excelFormatString;


    /**
     * Main constructor.
     *
     * @param text The text to be displayed in the cell.
     */
    public DefaultCellModel(T data) {
        this(data, null, null);
    }

    public DefaultCellModel(T data, Function<T, String> stringConverter, String excelFormatString) {
        this.data = data;
        this.stringConverter = stringConverter;
        this.excelFormatString = excelFormatString;
    }
    
    
    
    @Override
    public T getData() {
        return this.data;
    }
    
    @Override
    public String getExcelFormatString() {
        return this.excelFormatString;
    }
        
    @Override
    public String getText() {
        if (this.stringConverter == null) {
            return this.data == null ? "" : this.data.toString();
        } else {
            return this.stringConverter.apply(this.data);
        }
    }

    @Override
    public String getTooltip() {
        return tooltip;
    }

    /**
     * Sets the tooltip for this cell model.
     *
     * @param tooltip The tooltip for the cell model.
     *
     * @return As a utility, returns this.
     */
    public DefaultCellModel setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public HorizontalAlign getHorizontalAlignment() {
        return horizontalAlignment;
    }

    /**
     * Sets the horizontal alignment for this cell model.
     *
     * @param alignment The horizontal alignment for the cell model.
     *
     * @return As a utility, returns this.
     */
    public DefaultCellModel setHorizontalAlignment(CellModel.HorizontalAlign alignment) {
        this.horizontalAlignment = alignment;
        return this;
    }

    @Override
    public Insets getInsets() {
        return insets;
    }

    /**
     * Sets the insets for the text within the cell
     *
     * @param insets The insets.
     *
     * @return As a utility, returns this.
     */
    public DefaultCellModel setInsets(Insets insets) {
        this.insets = insets;
        return this;
    }

    @Override
    public List<MenuItem> getPopupMenu() {
        if (popupMenu != null) {
            return Collections.unmodifiableList(popupMenu);
        }

        if (menuItemSupplier != null) {
            return this.menuItemSupplier.get();
        }

        return null;
    }

    /**
     * Sets a function to lazy load the popup menu items.
     *
     * @param menuItemSupplier The lazy load function for popup items.
     * @return
     */
    public DefaultCellModel setPopupMenuRetriever(Supplier<List<MenuItem>> menuItemSupplier) {
        this.menuItemSupplier = menuItemSupplier;
        return this;
    }

    /**
     * Sets the list of items for a popup menu
     *
     * @param popupMenu
     * @return As a utility, returns this.
     */
    public DefaultCellModel setPopupMenu(List<MenuItem> popupMenu) {
        this.popupMenu = popupMenu == null ? null : new ArrayList<>(popupMenu);
        return this;
    }

    @Override
    public String toString() {
        return getText();
    }
}
