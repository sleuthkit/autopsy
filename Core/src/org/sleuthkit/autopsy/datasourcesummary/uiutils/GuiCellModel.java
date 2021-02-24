/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.Insets;
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
     * @return The insets for the cell text.
     */
    Insets getInsets();

    /**
     * @return The popup menu associated with this cell or null if no popup menu
     * should be shown for this cell.
     */
    List<MenuItem> getPopupMenu();

}
