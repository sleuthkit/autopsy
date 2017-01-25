/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ServiceProvider;





    @ActionID(category = "Tools", id = "org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModuleAction")
@ActionRegistration(
        displayName = "#CTL_RunIngestModulesAction", lazy = false)
@ActionReference(path = "Toolbars/Tools", position = 200)
    @ServiceProvider(service = RunIngestModuleAction.class)
@Messages("CTL_RunIngestModulesAction=Run Ingest Modules Wizard")
public final class RunIngestModuleAction extends CallableSystemAction implements Presenter.Toolbar {
    public static ActionListener run() {
        return new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                WizardDescriptor wiz = new WizardDescriptor(new RunIngestModuleWizardWizardIterator());
                // {0} will be replaced by WizardDescriptor.Panel.getComponent().getName()
                // {1} will be replaced by WizardDescriptor.Iterator.name()
                wiz.setTitleFormat(new MessageFormat("{0} ({1})"));
                wiz.setTitle("...dialog title...");
                if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
                }
            }
        };
    }

    @Override
    public void performAction() {
        
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public HelpCtx getHelpCtx() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
