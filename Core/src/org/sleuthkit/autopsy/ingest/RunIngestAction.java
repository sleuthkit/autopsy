/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JMenuItem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;

@ActionID(
        category = "Tools",
        id = "org.sleuthkit.autopsy.ingest.RunIngestAction"
)
@ActionRegistration(
        displayName = "#CTL_RunIngestAction"
)
@Messages("CTL_RunIngestAction=Run Ingest")
public final class RunIngestAction extends CallableSystemAction implements Presenter.Menu, ActionListener {

    static public RunIngestAction getInstance() {
        return new RunIngestAction();
    }

    /**
     * This method does nothing. Use the actionPerformed instead of this method.
     */
    @Override
    public void performAction() {
        getMenuPresenter();
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(RunIngestAction.class, "RunIngestModulesMenu.getName.text");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    @Override
    public JMenuItem getMenuPresenter() {
        JMenuItem sublist = new UpdateIngestImages();
        sublist.setVisible(true);
        return sublist;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
    }
}
