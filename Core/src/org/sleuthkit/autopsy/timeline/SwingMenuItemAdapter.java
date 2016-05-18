/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

//TODO: move this into CoreUtils? -jm
public class SwingMenuItemAdapter extends MenuItem {

    SwingMenuItemAdapter(final JMenuItem jMenuItem) {
        super(jMenuItem.getText());
        setOnAction(actionEvent -> SwingUtilities.invokeLater(jMenuItem::doClick));
    }

    public static MenuItem create(MenuElement jmenuItem) {
        if (jmenuItem == null) {
            return new SeparatorMenuItem();
        } else if (jmenuItem instanceof JMenu) {
            return new SwingMenuAdapter((JMenu) jmenuItem);
        } else if (jmenuItem instanceof JPopupMenu) {
            return new SwingMenuAdapter((JPopupMenu) jmenuItem);
        } else {
            return new SwingMenuItemAdapter((JMenuItem) jmenuItem);
        }

    }

    private static class SwingMenuAdapter extends Menu {

        SwingMenuAdapter(final JMenu jMenu) {
            super(jMenu.getText());
            buildChildren(jMenu);
        }

        SwingMenuAdapter(JPopupMenu jPopupMenu) {
            super(jPopupMenu.getLabel());
            buildChildren(jPopupMenu);
        }

        private void buildChildren(MenuElement jMenu) {

            for (MenuElement menuE : jMenu.getSubElements()) {
                if (menuE instanceof JMenu) {
                    getItems().add(SwingMenuItemAdapter.create((JMenu) menuE));
                } else if (menuE instanceof JMenuItem) {
                    getItems().add(SwingMenuItemAdapter.create((JMenuItem) menuE));
                } else if (menuE instanceof JPopupMenu) {
                    buildChildren(menuE);
                } else {
                    System.out.println(menuE.toString());
//                throw new UnsupportedOperationException();
                }
            }
        }
    }

}
