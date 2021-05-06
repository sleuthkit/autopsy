/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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

import com.google.common.util.concurrent.ListenableFuture;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
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
import org.sleuthkit.autopsy.imagegallery.ImageGalleryPreferences;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryTopComponent;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB.DrawableDbBuildStatusEnum;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
import org.sleuthkit.autopsy.imagegallery.utils.TaskUtils;
import static org.sleuthkit.autopsy.imagegallery.utils.TaskUtils.addFXCallback;
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
    + "Choosing 'yes' will update the database and enable listening to future ingests.\n\n"
            + "Database update status will appear in the lower right corner of the application window.",
    "OpenAction.notAnalyzedDlg.msg=No image/video files available to display yet.\n"
    + "Please run FileType and EXIF ingest modules.",
    "OpenAction.stale.confDlg.title=Image Gallery"})
public final class OpenAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenAction.class.getName());
    private static final String VIEW_IMAGES_VIDEOS = Bundle.CTL_OpenAction();

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
    @NbBundle.Messages({"OpenAction.dialogTitle=Image Gallery",
        "OpenAction.multiUserDialog.Header=Multi-user Image Gallery",
        "OpenAction.multiUserDialog.ContentText=The Image Gallery updates itself differently for multi-user cases than single user cases. Notably:\n\n"
        + "If your computer is analyzing a data source, then you will get real-time Image Gallery updates as files are analyzed (hashed, EXIF, etc.). This is the same behavior as a single-user case.\n\n"
        + "If another computer in your multi-user cluster is analyzing a data source, you will get updates about files on that data source only when you launch Image Gallery, which will cause the local database to be rebuilt based on results from other nodes.",
        "OpenAction.multiUserDialog.checkBox.text=Don't show this message again.",
        "OpenAction.noControllerDialog.header=Cannot open Image Gallery",        
        "OpenAction.noControllerDialog.text=An initialization error ocurred.\nPlease see the log for details.",
    })
    public void performAction() {
        //check case
        final Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "No current case", ex);
            return;
        }
        Platform.runLater(() -> {
            ImageGalleryController controller;
            // @@@ This call gets a lock. We shouldn't do this in the UI....
            controller = ImageGalleryController.getController(currentCase);

            // Display an error if we could not get the controller and return
            if (controller == null) {
                Alert errorDIalog = new Alert(Alert.AlertType.ERROR);
                errorDIalog.initModality(Modality.APPLICATION_MODAL);
                errorDIalog.setResizable(true);
                errorDIalog.setTitle(Bundle.OpenAction_dialogTitle());
                errorDIalog.setHeaderText(Bundle.OpenAction_noControllerDialog_header());
                Label errorLabel = new Label(Bundle.OpenAction_noControllerDialog_text());
                errorLabel.setMaxWidth(450);
                errorLabel.setWrapText(true);
                errorDIalog.getDialogPane().setContent(new VBox(10, errorLabel));
                GuiUtils.setDialogIcons(errorDIalog);
                errorDIalog.showAndWait();
                logger.log(Level.SEVERE, "No Image Gallery controller for the current case");  
                return;
            }

            // Make sure the user is aware of Single vs Multi-user behaviors
            if (currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE
                    && ImageGalleryPreferences.isMultiUserCaseInfoDialogDisabled() == false) {
                Alert dialog = new Alert(Alert.AlertType.INFORMATION);
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.setResizable(true);
                dialog.setTitle(Bundle.OpenAction_dialogTitle());
                dialog.setHeaderText(Bundle.OpenAction_multiUserDialog_Header());

                Label label = new Label(Bundle.OpenAction_multiUserDialog_ContentText());
                label.setMaxWidth(450);
                label.setWrapText(true);
                CheckBox dontShowAgainCheckBox = new CheckBox(Bundle.OpenAction_multiUserDialog_checkBox_text());
                dialog.getDialogPane().setContent(new VBox(10, label, dontShowAgainCheckBox));
                GuiUtils.setDialogIcons(dialog);

                dialog.showAndWait();

                if (dialog.getResult() == ButtonType.OK && dontShowAgainCheckBox.isSelected()) {
                    ImageGalleryPreferences.setMultiUserCaseInfoDialogDisabled(true);
                }
            }

            checkDBStale(controller);
        });
    }

    private void checkDBStale(ImageGalleryController controller) {

        ListenableFuture<Map<Long, DrawableDB.DrawableDbBuildStatusEnum>> dataSourceStatusMapFuture = TaskUtils.getExecutorForClass(OpenAction.class)
                .submit(controller::getAllDataSourcesDrawableDBStatus);

        addFXCallback(dataSourceStatusMapFuture,
                dataSourceStatusMap -> {
                    int numStale = 0;
                    int numNoAnalysis = 0;
                    // NOTE: There is some overlapping code here with Controller.getStaleDataSourceIds().  We could possibly just use
                    // that method to figure out stale and then do more simple stuff here to figure out if there is no data at all
                    for (Map.Entry<Long, DrawableDbBuildStatusEnum> entry : dataSourceStatusMap.entrySet()) {
                        DrawableDbBuildStatusEnum status = entry.getValue();
                        if (DrawableDbBuildStatusEnum.UNKNOWN == status) {
                            try {
                                // likely a data source analyzed on a remote node in multi-user case OR single-user case with listening off
                                if (controller.hasFilesWithMimeType(entry.getKey())) {
                                    numStale++;
                                    // likely a data source (local or remote) that has no analysis yet (note there is also IN_PROGRESS state)
                                } else {
                                    numNoAnalysis++;
                                }
                            } catch (TskCoreException ex) {
                                logger.log(Level.SEVERE, "Error querying case database", ex);
                            }
                        } // was already rebuilt, but wasn't complete at the end
                        else if (DrawableDbBuildStatusEnum.REBUILT_STALE == status) {
                            numStale++;
                        }
                    }

                    // NOTE: we are running on the fx thread.
                    // If there are any that are STALE, give them a prompt to do so. 
                    if (numStale > 0) {
                        // See if user wants to rebuild, cancel out, or open as is
                        Alert alert = new Alert(Alert.AlertType.WARNING,
                                Bundle.OpenAction_stale_confDlg_msg(),
                                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.setTitle(Bundle.OpenAction_stale_confDlg_title());
                        GuiUtils.setDialogIcons(alert);
                        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);

                        if (answer == ButtonType.CANCEL) {
                            //just do nothing - don't open window
                            return;
                        } else if (answer == ButtonType.NO) {
                            // They don't want to rebuild. Just open the UI as is.
                            // NOTE: There could be no data....
                        } else if (answer == ButtonType.YES) {
                            controller.rebuildDrawablesDb();
                        }
                        openTopComponent();
                        return;
                    }

                    // if there is no data to display, then let them know
                    if (numNoAnalysis == dataSourceStatusMap.size()) {
                        // give them a dialog to enable modules if no data sources have been analyzed
                        Alert alert = new Alert(Alert.AlertType.WARNING, Bundle.OpenAction_notAnalyzedDlg_msg(), ButtonType.OK);
                        alert.setTitle(Bundle.OpenAction_stale_confDlg_title());
                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.showAndWait();
                        return;
                    }

                    // otherwise, lets open the UI
                    openTopComponent();
                },
                throwable -> logger.log(Level.SEVERE, "Error checking if drawable db is stale.", throwable)//NON-NLS
        );
    }

    @Messages({"OpenAction.openTopComponent.error.message=An error occurred while attempting to open Image Gallery.",
               "OpenAction.openTopComponent.error.title=Failed to open Image Gallery"})
    private void openTopComponent() {
        SwingUtilities.invokeLater(() -> {
            try {
                ImageGalleryTopComponent.openTopComponent();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to open Image Gallery top component", ex); //NON-NLS}
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.OpenAction_openTopComponent_error_message(), Bundle.OpenAction_openTopComponent_error_title(), JOptionPane.PLAIN_MESSAGE);
            }
        });
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
        return true; // run off edt
    }
}
