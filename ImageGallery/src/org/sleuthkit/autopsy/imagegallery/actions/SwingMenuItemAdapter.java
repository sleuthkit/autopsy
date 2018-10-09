/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

/**
 * Adpater that allows Swing menus to be used in JavaFx UIs.
 *
 * jira-1062: Is this generaly usefull? should we move into CoreUtils? -jm
 */
public class SwingMenuItemAdapter extends MenuItem {

    JMenuItem jMenuItem;

    SwingMenuItemAdapter(final JMenuItem jMenuItem) {
        super(jMenuItem.getText());
        this.jMenuItem = jMenuItem;
        setOnAction(actionEvent -> SwingUtilities.invokeLater(jMenuItem::doClick)
        );
    }

    public static MenuItem create(MenuElement jmenuItem) {
        if (jmenuItem instanceof JMenu) {
            return new SwingMenuAdapter((JMenu) jmenuItem);
        } else if (jmenuItem instanceof JPopupMenu) {
            return new SwingMenuAdapter((JPopupMenu) jmenuItem);
        } else {
            return new SwingMenuItemAdapter((JMenuItem) jmenuItem);
        }

    }
}

class SwingMenuAdapter extends Menu {

    private final MenuElement jMenu;

    SwingMenuAdapter(final JMenu jMenu) {
        super(jMenu.getText());
        this.jMenu = jMenu;
        if (!jMenu.isEnabled()) {
            /* Grey out text if the JMenu that this Menu is wrapping is not
             * enabled. */
            setDisable(true);
        }
        buildChildren(jMenu);

    }

    SwingMenuAdapter(JPopupMenu jPopupMenu) {
        super(jPopupMenu.getLabel());
        this.jMenu = jPopupMenu;

        buildChildren(jMenu);
    }

    private void buildChildren(MenuElement jMenu) {

        for (MenuElement menuElement : jMenu.getSubElements()) {
            if (menuElement instanceof JMenu) {
                getItems().add(SwingMenuItemAdapter.create((JMenu) menuElement));
            } else if (menuElement instanceof JMenuItem) {
                getItems().add(SwingMenuItemAdapter.create((JMenuItem) menuElement));
            } else if (menuElement instanceof JPopupMenu) {
                buildChildren(menuElement);
            } else {
                throw new UnsupportedOperationException("Unown MenuElement subclass: " + menuElement.getClass().getName());
            }
        }
    }
}
