/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;

/**
 * An action that shows a popup that enumerates the deletion status of the
 * various parts of a multi-user case known to the coordination service.
 */
final class ShowCaseDeletionStatusAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final CaseNodeData caseNodeData;

    /**
     * Constructs an action that shows a popup that enumerates the deletion
     * status of the various parts of a multi-user case known to the
     * coordination service.
     *
     * @param caseNodeData The coordination service node data for the case.
     */
    @NbBundle.Messages({
        "ShowCaseDeletionStatusAction.menuItemText=Show Deletion Status"
    })
    ShowCaseDeletionStatusAction(CaseNodeData caseNodeData) {
        super(Bundle.ShowCaseDeletionStatusAction_menuItemText());
        this.caseNodeData = caseNodeData;
        setEnabled(false); // RJCTODO: Enable when implemented
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // RJCTODO: Implement
    }

    @Override
    public ShowCaseDeletionStatusAction clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

}
