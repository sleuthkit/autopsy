/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.Collection;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;

/**
 *
 *
 */
abstract class AbstractCVTAction extends AbstractAction implements Presenter.Popup {

    /**
     * Get the selected accounts that will be acted upon.
     *
     * @return The selected accounts
     */
    Collection<? extends AccountDeviceInstanceKey> getSelectedAccounts() {
        return Utilities.actionsGlobalContext().lookupAll(AccountDeviceInstanceKey.class);
    }

    @Override
    final public void putValue(String key, Object newValue) {
        super.putValue(key, newValue);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenuItem presenter = new JMenuItem(this);
        presenter.setText(getActionDisplayName());
        presenter.setIcon(getIcon());
        return presenter;
    }

    abstract String getActionDisplayName();

    abstract ImageIcon getIcon();

}
