/*
* Autopsy Forensic Browser
*
* Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.imagegallery.OpenAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 101),
    @ActionReference(path = "Toolbars/Case", position = 101)
})
@ActionRegistration(displayName = "#CTL_OpenAction", lazy = false)
@Messages({"CTL_OpenAction=View Images/Videos",
    "OpenAction.stale.confDlg.msg=The image / video database may be out of date. " +
            "Do you want to update and listen for further ingest results?\n" +
            "Choosing 'yes' will update the database and enable listening to future ingests.",
    "OpenAction.stale.confDlg.title=Image Gallery"})
public final class OpenAction extends CallableSystemAction implements Presenter.Toolbar {
    
    private static final String VIEW_IMAGES_VIDEOS = Bundle.CTL_OpenAction();
    private static final boolean fxInited = Installer.isJavaFxInited();
    private static final Logger LOGGER = Logger.getLogger(OpenAction.class.getName());
    private JButton toolbarButton = new JButton();
    private final PropertyChangeListener pcl;
    
    public OpenAction() {
        super();
        toolbarButton.addActionListener(actionEvent -> performAction());
        pcl = (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                setEnabled(Case.isCaseOpen());
            }
        };
        Case.addPropertyChangeListener(pcl);
        this.setEnabled(false);
    }
    
    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen() && fxInited && Case.getCurrentCase().hasData();
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
        
        //check case
        if (!Case.isCaseOpen()) {
            return;
        }
        final Case currentCase = Case.getCurrentCase();
        
        if (ImageGalleryModule.isDrawableDBStale(currentCase)) {
            //drawable db is stale, ask what to do
            int answer = JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), Bundle.OpenAction_stale_confDlg_msg(),
                    Bundle.OpenAction_stale_confDlg_title(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            
            switch (answer) {
                case JOptionPane.YES_OPTION:
                    ImageGalleryController.getDefault().setListeningEnabled(true);
                    //fall through
                case JOptionPane.NO_OPTION:
                    ImageGalleryTopComponent.openTopComponent();
                    break;
                case JOptionPane.CANCEL_OPTION:
                    break; //do nothing
            }
        } else {
            //drawable db is not stale, just open it
            ImageGalleryTopComponent.openTopComponent();
        }
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
