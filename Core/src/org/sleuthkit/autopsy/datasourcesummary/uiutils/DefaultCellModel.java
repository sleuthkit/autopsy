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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The default cell model.
 */
public class DefaultCellModel<T> implements GuiCellModel {

    private final T data;
    private final String text;
    private String tooltip;
    private CellModel.HorizontalAlign horizontalAlignment;
    private List<MenuItem> popupMenu;
    private Supplier<List<MenuItem>> menuItemSupplier;

    /**
     * Main constructor.
     *
     * @param data The data to be displayed in the cell.
     */
    public DefaultCellModel(T data) {
        this(data, null);
    }

    /**
     * Constructor.
     *
     * @param data              The data to be displayed in the cell.
     * @param stringConverter   The means of converting that data to a string or
     *                          null to use .toString method on object.
     */
    public DefaultCellModel(T data, Function<T, String> stringConverter) {
        this.data = data;

        if (stringConverter == null) {
            text = this.data == null ? "" : this.data.toString();
        } else {
            text = stringConverter.apply(this.data);
        }
        this.tooltip = text;
    }

    @Override
    public T getData() {
        return this.data;
    }

    @Override
    public String getText() {
        return text;
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
     *
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
     *
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
