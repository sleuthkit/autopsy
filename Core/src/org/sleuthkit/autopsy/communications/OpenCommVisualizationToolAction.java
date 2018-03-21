/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;

/**
 * Action that opens the CVT. Available through the Tools menu and the main
 * toolbar.
 */
@ActionID(category = "Tools",
        id = "org.sleuthkit.autopsy.communicationsVisualization.OpenCVTAction")
@ActionRegistration(displayName = "#CTL_OpenCVTAction", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 102)
    ,
 @ActionReference(path = "Toolbars/Case", position = 102)})
@Messages("CTL_OpenCVTAction=Communications")
public final class OpenCommVisualizationToolAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private final PropertyChangeListener pcl;
    private final JButton toolbarButton = new JButton(getName(),
            new ImageIcon(getClass().getResource("images/emblem-web24.png"))); //NON-NLS

    public OpenCommVisualizationToolAction() {
        toolbarButton.addActionListener(actionEvent -> performAction());
        setEnabled(false); //disabled by default.  Will be enabled in Case.java when a case is opened.
        pcl = (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                setEnabled(RuntimeProperties.runningWithGUI() && evt.getNewValue() != null);
            }
        };
        Case.addPropertyChangeListener(pcl);

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

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        toolbarButton.setEnabled(value);
    }

    @Override
    @NbBundle.Messages("OpenCVTAction.displayName=Communications")
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
