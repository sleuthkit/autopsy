/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

public final class SetShowRejected extends AbstractAction {

    private final Accounts accounts;

    public SetShowRejected(Accounts accounts) {
        super("Show Rejected Results");
        this.accounts = accounts;

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        accounts.showRejected(accounts.isShowRejected() == false);
    }
}
