/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

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
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;

/**
 * Action for opening the Geolocation window. This action is 
 * currently available through the Tools menu.
 *
 */
@ActionID(category = "Tools",
        id = "org.sleuthkit.autopsy.geolocation.OpenGeolocationAction")
@ActionRegistration(displayName = "#CTL_OpenGeolocation", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 103),
@ActionReference(path = "Toolbars/Case", position = 103)})
@Messages({"CTL_OpenGeolocation=Geolocation"})
public class OpenGeolocationAction extends CallableSystemAction {
    
    private static final long serialVersionUID = 1L;
    private final JButton toolbarButton = new JButton(getName(),
            new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/blueGeo24.png"))); //NON-NLS
    
    /**
     * Constructs the new action of opening the Geolocation window.
     */
    public OpenGeolocationAction() {
        toolbarButton.addActionListener(actionEvent -> performAction());
        setEnabled(false); //disabled by default.  Will be enabled in Case.java when a case is opened.

        PropertyChangeListener caseChangeListener = (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                setEnabled(RuntimeProperties.runningWithGUI() && evt.getNewValue() != null);
            }
        };

        Case.addPropertyChangeListener(caseChangeListener);
    }

    @Override
    public void performAction() {
        final TopComponent topComponent = WindowManager.getDefault().findTopComponent("GeolocationTopComponent");
        if (topComponent != null) {
            if (topComponent.isOpened() == false) {
                topComponent.open();
            }
            topComponent.toFront();
            topComponent.requestActive();
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
    public String getName() {
        return Bundle.CTL_OpenGeolocation();
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
