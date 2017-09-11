/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.menuactions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Menu item tracks the DataResult windows
 */
class DataResultMenu extends CallableSystemAction implements Presenter.Menu, PropertyChangeListener {

    JMenu menu = new JMenu(NbBundle.getMessage(this.getClass(), "DataResultMenu.menu.dataResWin.text"));

    DataResultMenu() {
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return new SearchResultMenu();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String changed = evt.getPropertyName();
        Object oldValue = evt.getOldValue();
        Object newValue = evt.getNewValue();

        if (changed.equals(Case.Events.CURRENT_CASE.toString())) {
            if (newValue != null) {
                // enable all menus when a case is created / opened
                int totalMenus = menu.getItemCount();
                for (int i = 0; i < totalMenus; i++) {
                    menu.getItem(i).setEnabled(true);
                }
            } else {
                // disable all menus when the case is closed
                int totalMenus = menu.getItemCount();
                for (int i = 0; i < totalMenus; i++) {
                    menu.getItem(i).setEnabled(false);
                }
            }
        }
    }

    @Override
    public void performAction() {

    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "DataResultMenu.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
