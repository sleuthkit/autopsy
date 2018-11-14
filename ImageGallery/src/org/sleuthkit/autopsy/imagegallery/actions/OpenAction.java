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
import javax.swing.SwingUtilities;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.Installer;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
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
    + "Choosing 'yes' will update the database and enable listening to future ingests.",
    "OpenAction.notAnalyzedDlg.msg=No image/video files available to display yet.\n"
        + "Please run FileType and EXIF ingest modules.",
    "OpenAction.stale.confDlg.title=Image Gallery"})
public final class OpenAction extends CallableSystemAction {

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
        "OpenAction.multiUserDialog.checkBox.text=Don't show this message again."})
    public void performAction() {
        //check case
        final Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }
        ImageGalleryController controller;
        try {
            controller = ImageGalleryModule.getController();
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting ImageGalleryController for current case.", ex);
            return;
        }
        Platform.runLater(() -> {
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
        
        ListenableFuture<Map<Long, DrawableDB.DrawableDbBuildStatusEnum>> dataSourceStatusMapFuture =  TaskUtils.getExecutorForClass(OpenAction.class)
                .submit(controller::getAllDataSourcesDrawableDBStatus);
        
        addFXCallback(dataSourceStatusMapFuture,
                dataSourceStatusMap -> {
                    
                    boolean dbIsStale = false;
                    for (Map.Entry<Long, DrawableDbBuildStatusEnum> entry : dataSourceStatusMap.entrySet()) {
                        DrawableDbBuildStatusEnum status = entry.getValue();
                        if (DrawableDbBuildStatusEnum.COMPLETE != status) {
                           dbIsStale = true;
                        }
                    }               
             
                    //back on fx thread.
                    if (false == dbIsStale) {
                        //drawable db is not stale, just open it
                        openTopComponent();
                    } else {
                        
                        // If there is only one datasource and it's in DEFAULT State - 
                        // ingest modules need to be run on the data source
                        if  (dataSourceStatusMap.size()== 1) {
                            Map.Entry<Long, DrawableDB.DrawableDbBuildStatusEnum> entry = dataSourceStatusMap.entrySet().iterator().next();
                            if (entry.getValue() == DrawableDbBuildStatusEnum.DEFAULT ) {
                                Alert alert = new Alert(Alert.AlertType.WARNING, Bundle.OpenAction_notAnalyzedDlg_msg(), ButtonType.OK);
                                alert.setTitle(Bundle.OpenAction_stale_confDlg_title());
                                alert.initModality(Modality.APPLICATION_MODAL);

                                alert.showAndWait();
                                return;
                            }
                        } 
                        
                        //drawable db is stale,
                        //ask what to do
                        Alert alert = new Alert(Alert.AlertType.WARNING,
                                Bundle.OpenAction_stale_confDlg_msg(),
                                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.setTitle(Bundle.OpenAction_stale_confDlg_title());
                        GuiUtils.setDialogIcons(alert);
                        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
                        if (answer == ButtonType.CANCEL) {
                            //just do nothing
                        } else if (answer == ButtonType.NO) {
                            openTopComponent();
                        } else if (answer == ButtonType.YES) {
                            if (controller.getAutopsyCase().getCaseType() == Case.CaseType.SINGLE_USER_CASE) {
                                /* For a single-user case, we favor user
                                 * experience, and rebuild the database as soon
                                 * as Image Gallery is enabled for the case.
                                 *
                                 * Turning listening off is necessary in order
                                 * to invoke the listener that will call
                                 * controller.rebuildDB();
                                 */
                                controller.setListeningEnabled(false);
                                controller.setListeningEnabled(true);
                            } else {
                                /*
                                 * For a multi-user case, we favor overall
                                 * performance and user experience, not every
                                 * user may want to review images, so we rebuild
                                 * the database only when a user launches Image
                                 * Gallery.
                                 */
                                controller.rebuildDB();
                            }
                            openTopComponent();
                        }
                    }
                },
                throwable -> logger.log(Level.SEVERE, "Error checking if drawable db is stale.", throwable)//NON-NLS
        );
    }

    private void openTopComponent() {
        SwingUtilities.invokeLater(() -> {
            try {
                ImageGalleryTopComponent.openTopComponent();
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Attempted to access ImageGallery with no case open.", ex);//NON-NLS
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting ImageGalleryController.", ex); //NON-NLS}
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
