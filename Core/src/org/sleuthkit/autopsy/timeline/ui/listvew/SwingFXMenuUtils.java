/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.listvew;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

/**
 * Allows creation of JavaFX menus with the same structure as Swing menus and
 * which invoke the same actions.
 */
class SwingFXMenuUtils {

    private SwingFXMenuUtils() {
    }

    /**
     * Factory method that creates a JavaFX MenuItem backed by a swing
     * MenuElement
     *
     * @param jMenuElement The MenuElement to create a JavaFX menu for.
     *
     * @return a MenuItem for the given MenuElement
     */
    public static MenuItem createFXMenu(MenuElement jMenuElement) {
        if (jMenuElement == null) {
            //Since null is sometime used to represenet a seperator, follow that convention.
            return new SeparatorMenuItem();
        } else if (jMenuElement instanceof JMenu) {
            return new MenuAdapter((JMenu) jMenuElement);
        } else if (jMenuElement instanceof JPopupMenu) {
            return new MenuAdapter((JPopupMenu) jMenuElement);
        } else {
            return new MenuItemAdapter((JMenuItem) jMenuElement);
        }
    }

    /**
     * A JavaFX MenuItem that invokes the backing JMenuItem when clicked.
     */
    private static class MenuItemAdapter extends MenuItem {

        private MenuItemAdapter(final JMenuItem jMenuItem) {
            super(jMenuItem.getText());
            setDisable(jMenuItem.isEnabled() == false);
            setOnAction(actionEvent -> SwingUtilities.invokeLater(jMenuItem::doClick));
        }
    }

    /**
     * A JavaFX Menu that has the same structure as a given Swing JMenu or
     * JPopupMenu.
     */
    private static class MenuAdapter extends Menu {

        /**
         * Constructor for JMenu
         *
         * @param jMenu The JMenu to parallel in this Menu.
         */
        MenuAdapter(final JMenu jMenu) {
            super(jMenu.getText());
            setDisable(jMenu.isEnabled() == false);
            populateSubMenus(jMenu);
        }

        /**
         * Constructor for JPopupMenu
         *
         * @param jPopupMenu The JPopupMenu to parallel in this Menu.
         */
        MenuAdapter(JPopupMenu jPopupMenu) {
            super(jPopupMenu.getLabel());
            setDisable(jPopupMenu.isEnabled() == false);
            populateSubMenus(jPopupMenu);
        }

        /**
         * Populate the sub menus of this menu.
         *
         * @param menu The MenuElement whose sub elements will be used to
         *             populate the sub menus of this menu.
         */
        private void populateSubMenus(MenuElement menu) {
            for (MenuElement menuElement : menu.getSubElements()) {
                if (menuElement == null) {
                    //Since null is sometime used to represenet a seperator, follow that convention.
                    getItems().add(new SeparatorMenuItem());

                } else if (menuElement instanceof JMenuItem) {
                    getItems().add(SwingFXMenuUtils.createFXMenu(menuElement));

                } else if (menuElement instanceof JPopupMenu) {
                    populateSubMenus(menuElement);
                } else {
                    throw new UnsupportedOperationException("Unown MenuElement subclass: " + menuElement.getClass().getName());
                }
            }
        }
    }
}
