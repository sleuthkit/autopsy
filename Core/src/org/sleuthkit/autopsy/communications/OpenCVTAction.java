/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

@ActionID(category = "Tools",
        id = "org.sleuthkit.autopsy.communicationsVisualization.OpenCVTAction")
@ActionRegistration(displayName = "#CTL_OpenCVTAction", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 102)
    ,    @ActionReference(path = "Toolbars/Case", position = 102)})
@Messages("CTL_OpenCVTAction=Visualize Communications")
public final class OpenCVTAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final long serialVersionUID = 1L;

    private final JButton toolbarButton = new JButton(getName(),
            new ImageIcon(getClass().getResource("images/email_link.png"))); //NON-NLS

    public OpenCVTAction() {
        toolbarButton.addActionListener(actionEvent -> performAction());
    }

    @Override
    public void performAction() {
        final TopComponent tc = WindowManager.getDefault().findTopComponent("CVTTopComponent");
        if (tc != null) {
            if (tc.isOpened() == false) {
                tc.open();
            }
            tc.toFront();
            tc.requestActive();
        }
    }

    @Override
    @NbBundle.Messages("OpenCVTAction.displayName=Communications Visualizaton")
    public String getName() {
        return Bundle.OpenCVTAction_displayName();
    }

    /**
     * Returns the toolbar component of this action
     *
     * @return component the toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        return toolbarButton;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
