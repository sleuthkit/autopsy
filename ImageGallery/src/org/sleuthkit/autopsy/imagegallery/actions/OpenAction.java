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
import javax.swing.JButton;
import javax.swing.JOptionPane;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;

@ActionID(category = "Tools",
        id = "org.sleuthkit.autopsy.imagegallery.OpenAction")
@ActionReference(path = "Menu/Tools" /* , position = 333 */)
@ActionRegistration( //        iconBase = "org/sleuthkit/autopsy/imagegallery/images/lightbulb.png",
        lazy = false,
        displayName = "#CTL_OpenAction")
@Messages({"CTL_OpenAction=View Images/Videos",
        "OpenAction.stale.confDlg.msg=The image / video database may be out of date. " +
                "Do you want to update and listen for further ingest results?\n" +
                "Choosing 'yes' will update the database and enable listening to future ingests.",
        "OpenAction.stale.confDlg.title=Image Gallery"})
public final class OpenAction extends CallableSystemAction {

    private static final String VIEW_IMAGES_VIDEOS = Bundle.CTL_OpenAction();

    private static final boolean fxInited = Installer.isJavaFxInited();

    private static final Logger LOGGER = Logger.getLogger(OpenAction.class.getName());

    public OpenAction() {
        super();
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
        JButton toolbarButton = new JButton(this);
        toolbarButton.setText(VIEW_IMAGES_VIDEOS);
        toolbarButton.addActionListener(this);

        return toolbarButton;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {

        //check case
        if (!Case.existsCurrentCase()) {
            return;
        }
        final Case currentCase = Case.getCurrentCase();

        if (ImageGalleryModule.isDrawableDBStale(currentCase)) {
            //drawable db is stale, ask what to do
            int answer = JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(), Bundle.OpenAction.stale.confDlg.msg(),
                    Bundle.OpenAction_stale_confDlg.title(), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

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
