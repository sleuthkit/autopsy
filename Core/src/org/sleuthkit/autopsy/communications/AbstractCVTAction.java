/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.util.Collection;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;

/**
 * Base class for actions that act on the selected AccountDeviceInstanceKeys.
 * getPopupPresenter() provides a JMenuItem that works (i.e., has an icon) in
 * custom context menus and also in the Netbeans Explorer views.
 */
abstract class AbstractCVTAction extends AbstractAction implements Presenter.Popup {

    /**
     * Get the selected accounts that will be acted upon.
     *
     * @return The selected accounts
     */
    Collection<? extends AccountDeviceInstanceKey> getSelectedAccounts() {
        return  Utilities.actionsGlobalContext().lookupAll(AccountDeviceInstanceKey.class);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenuItem presenter = new JMenuItem(this);
        presenter.setText(getActionDisplayName());
        presenter.setIcon(getIcon());
        return presenter;
    }

    /**
     * The name/text of the action as displayed in a menu.
     *
     * @return The diaplay name of this action
     */
    abstract String getActionDisplayName();

    /**
     * The icon to use for this icon.
     *
     * @return An ImageIcon used to represent this action.
     */
    abstract ImageIcon getIcon();
}
