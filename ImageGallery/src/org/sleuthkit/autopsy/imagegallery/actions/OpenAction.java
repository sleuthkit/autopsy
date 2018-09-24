/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
import org.sleuthkit.datamodel.TskCoreException;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.imagegallery.OpenAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 101)
    ,
    @ActionReference(path = "Toolbars/Case", position = 101)})
@ActionRegistration(displayName = "#CTL_OpenAction", lazy = false)
@Messages({"CTL_OpenAction=Images/Videos",
    "OpenAction.stale.confDlg.msg=The image / video database may be out of date. "
    + "Do you want to update and listen for further ingest results?\n"
    + "Choosing 'yes' will update the database and enable listening to future ingests.",
    "OpenAction.stale.confDlg.title=Image Gallery"})
public final class OpenAction extends CallableSystemAction {

    private static final Logger logger = Logger.getLogger(OpenAction.class.getName());
    private static final String VIEW_IMAGES_VIDEOS = Bundle.CTL_OpenAction();
    private static final long FILE_LIMIT = 6_000_000;

    private final PropertyChangeListener pcl;
    private final JMenuItem menuItem;
    private final JButton toolbarButton = new JButton(this.getName(),
            new ImageIcon(getClass().getResource("btn_icon_image_gallery_26.png")));

    public OpenAction() {
        super();
        toolbarButton.addActionListener(actionEvent -> performAction());
        menuItem = super.getMenuPresenter();
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
        Case openCase;
        try {
            openCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            return false;
        }
        return super.isEnabled() && Installer.isJavaFxInited() && openCase.hasData();
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
    public JMenuItem getMenuPresenter() {
        return menuItem;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        menuItem.setEnabled(value);
        toolbarButton.setEnabled(value);
    }

    @Override
    @NbBundle.Messages({"OpenAction.dialogTitle=Image Gallery"})
    public void performAction() {

        //check case
        final Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }

        if (tooManyFiles()) {
            Platform.runLater(OpenAction::showTooManyFiles);
            setEnabled(false);
            return;
        }
        try {
              ImageGalleryController controller = ImageGalleryModule.getController();
            if (controller.isDataSourcesTableStale()) {
                //drawable db is stale, ask what to do
                int answer = JOptionPane.showConfirmDialog(
                        WindowManager.getDefault().getMainWindow(),
                        Bundle.OpenAction_stale_confDlg_msg(),
                        Bundle.OpenAction_stale_confDlg_title(),
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                switch (answer) {
                    case JOptionPane.YES_OPTION:
                        /* For a single-user case, we favor user experience, and
                         * rebuild the database as soon as Image Gallery is
                         * enabled for the case. For a multi-user case, we favor
                         * overall performance and user experience, not every
                         * user may want to review images, so we rebuild the
                         * database only when a user launches Image Gallery.
                         */
                      

                        if (currentCase.getCaseType() == Case.CaseType.SINGLE_USER_CASE) {
                            controller.setListeningEnabled(true);
                        } else {
                            controller.rebuildDB();
                        }
                        ImageGalleryTopComponent.openTopComponent();
                        break;

                    case JOptionPane.NO_OPTION: {
                        ImageGalleryTopComponent.openTopComponent();
                    }
                    break;
                    case JOptionPane.CANCEL_OPTION:
                        break; //do nothing
                }
            } else {
                //drawable db is not stale, just open it
                ImageGalleryTopComponent.openTopComponent();
            }
        } catch (NoCurrentCaseException noCurrentCaseException) {
            logger.log(Level.WARNING, "There was no case open when Image Gallery was opened.", noCurrentCaseException);
        }
    }

    private boolean tooManyFiles() {
        try {
            return FILE_LIMIT < Case.getCurrentCaseThrows().getSleuthkitCase().countFilesWhere("1 = 1");
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Can not open image gallery with no case open.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error counting files in the DB.", ex);
        }
        //if there is any doubt (no case, tskcore error, etc) just disable .
        return false;
    }

    @NbBundle.Messages({
        "ImageGallery.showTooManyFiles.contentText="
        + "There are too many files in the DB to ensure reasonable performance."
        + "  Image Gallery  will be disabled. ",
        "ImageGallery.showTooManyFiles.headerText="})
    private static void showTooManyFiles() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION,
                Bundle.ImageGallery_showTooManyFiles_contentText(), ButtonType.OK);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(Bundle.OpenAction_dialogTitle());
        GuiUtils.setDialogIcons(dialog);
        dialog.setHeaderText(Bundle.ImageGallery_showTooManyFiles_headerText());
        dialog.showAndWait();
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
