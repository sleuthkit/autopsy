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

import java.util.List;

/**
 * Basic interface for a cell model.
 */
public interface GuiCellModel extends CellModel {

    /**
     * A menu item to be used within a popup menu.
     */
    public interface MenuItem {

        /**
         * @return The title for that popup menu item.
         */
        String getTitle();

        /**
         * @return The action if that popup menu item is clicked.
         */
        Runnable getAction();
    }

    /**
     * Default implementation of a menu item.
     */
    public static class DefaultMenuItem implements MenuItem {

        private final String title;
        private final Runnable action;

        /**
         * Main constructor.
         *
         * @param title The title for the menu item.
         * @param action The action should the menu item be clicked.
         */
        public DefaultMenuItem(String title, Runnable action) {
            this.title = title;
            this.action = action;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Runnable getAction() {
            return action;
        }
    }

    /**
     * @return The popup menu associated with this cell or null if no popup menu
     * should be shown for this cell.
     */
    List<MenuItem> getPopupMenu();

}
