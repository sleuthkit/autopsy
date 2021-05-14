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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelCellModel;

/**
 * The default cell model.
 */
public class DefaultCellModel<T> implements GuiCellModel, ExcelCellModel {

    final T data;
    final Function<T, String> stringConverter;
    String tooltip;
    CellModel.HorizontalAlign horizontalAlignment;
    Insets insets;
    List<MenuItem> popupMenu;
    Supplier<List<MenuItem>> menuItemSupplier;
    final String excelFormatString;

    /**
     * Main constructor.
     *
     * @param data The data to be displayed in the cell.
     */
    public DefaultCellModel(T data) {
        this(data, null, null);
    }

    /**
     * Constructor.
     *
     * @param data The data to be displayed in the cell.
     * @param stringConverter The means of converting that data to a string or
     * null to use .toString method on object.
     */
    public DefaultCellModel(T data, Function<T, String> stringConverter) {
        this(data, stringConverter, null);
    }

    /**
     * Constructor.
     *
     * @param data The data to be displayed in the cell.
     * @param stringConverter The means of converting that data to a string or
     * null to use .toString method on object.
     * @param excelFormatString The apache poi excel format string to use with
     * the data.
     *
     * NOTE: Only certain data types can be exported. See
     * ExcelTableExport.createCell() for types.
     */
    public DefaultCellModel(T data, Function<T, String> stringConverter, String excelFormatString) {
        this.data = data;
        this.stringConverter = stringConverter;
        this.excelFormatString = excelFormatString;
        this.tooltip = getText();
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
    public DefaultCellModel<T> setTooltip(String tooltip) {
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
    public DefaultCellModel<T> setHorizontalAlignment(CellModel.HorizontalAlign alignment) {
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
    public DefaultCellModel<T> setInsets(Insets insets) {
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
    public DefaultCellModel<T> setPopupMenuRetriever(Supplier<List<MenuItem>> menuItemSupplier) {
        this.menuItemSupplier = menuItemSupplier;
        return this;
    }

    /**
     * Sets the list of items for a popup menu
     *
     * @param popupMenu
     * @return As a utility, returns this.
     */
    public DefaultCellModel<T> setPopupMenu(List<MenuItem> popupMenu) {
        this.popupMenu = popupMenu == null ? null : new ArrayList<>(popupMenu);
        return this;
    }

    @Override
    public String toString() {
        return getText();
    }
}
