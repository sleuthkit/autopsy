/*
* Autopsy Forensic Browser
*
* Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import static org.sleuthkit.autopsy.core.UserPreferences.SelectedMode.REVIEW;
import org.sleuthkit.autopsy.coreutils.Logger;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.experimental.autoingest.AutoIngestDashboardOpenAction")
@ActionReference(path = "Menu/Tools", position = 104)
@ActionRegistration(displayName = "#CTL_AutoIngestDashboardOpenAction", lazy = false)
@Messages({"CTL_AutoIngestDashboardOpenAction=Auto Ingest Dashboard"})
public final class AutoIngestDashboardOpenAction extends CallableSystemAction implements Presenter.Toolbar {
    
    private static final Logger LOGGER = Logger.getLogger(AutoIngestDashboardOpenAction.class.getName());
    private static final String VIEW_IMAGES_VIDEOS = Bundle.CTL_AutoIngestDashboardOpenAction();
    
    private final JButton toolbarButton = new JButton();
    private final PropertyChangeListener pcl;
    
    public AutoIngestDashboardOpenAction() {
        super();
        toolbarButton.addActionListener(actionEvent -> performAction());
        pcl = (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                 setEnabled(RuntimeProperties.runningWithGUI() && evt.getNewValue() != null);
            }
        };
        Case.addPropertyChangeListener(pcl);
        this.setEnabled(false);
    }
    
    @Override
    public boolean isEnabled() {
        UserPreferences.SelectedMode mode = UserPreferences.getMode();
        return (mode == REVIEW);
    }
    
    /** Returns the toolbar component of this action
     *
     * @return component the toolbar button */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("btn_icon_image_gallery_26.png")); //NON-NLS
        toolbarButton.setIcon(icon);
        toolbarButton.setText(this.getName());
        return toolbarButton;
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
    @SuppressWarnings("fallthrough")
    public void performAction() {
        AutoIngestDashboardTopComponent.openTopComponent();
    }
    
    @Override
    public String getName() {
        return VIEW_IMAGES_VIDEOS;
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