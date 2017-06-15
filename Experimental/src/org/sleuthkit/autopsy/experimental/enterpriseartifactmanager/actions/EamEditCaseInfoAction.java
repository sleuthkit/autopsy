/*
 * Enterprise Artifact Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.actions;

import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamCase;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDb;

/**
 * Action to update case details in enterprise artifact manager database
 */
@ActionID(
        category = "Case",
        id = "org.sleuthkit.enterpriseartifactmanager.actions.EnterpriseArtifactManagerCaseEditCaseInfoAction"
)
@ActionRegistration(
        displayName = "#CTL_EnterpriseArtifactManagerCaseEditCaseInfo",
        lazy = false
)
@ActionReference(path = "Menu/Case", position = 650, separatorAfter = 824)
@Messages("CTL_EnterpriseArtifactManagerCaseEditCaseInfo=Enterprise Artifact Manager Case Details")
public final class EamEditCaseInfoAction extends CallableSystemAction implements ActionListener {

    private final static Logger LOGGER = Logger.getLogger(EamEditCaseInfoAction.class.getName());

    private static JDialog popUpWindow;

    EamEditCaseInfoAction() {
        putValue(Action.NAME, Bundle.CTL_EnterpriseArtifactManagerCaseEditCaseInfo()); // put the action Name
        this.setEnabled(true);
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                popUpWindow = null;
            }
        });
    }

    @Override
    public boolean isEnabled() {
        boolean enabled = Boolean.valueOf(ModuleSettings.getConfigSetting("EnterpriseArtifactManager", "db.enabled")); // NON-NLS
        return enabled && Case.isCaseOpen();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        performAction();
    }

    @Override
    @Messages({"EnterpriseArtifactManagerCaseEditDetails.window.title=Edit Case Details"})
    public void performAction() {

        if (popUpWindow == null) {
            String curCaseUUID = Case.getCurrentCase().getName();

            // create the popUp window for it
            String title = Bundle.EnterpriseArtifactManagerCaseEditDetails_window_title();
            popUpWindow = new JDialog((JFrame) WindowManager.getDefault().getMainWindow(), title, false);
            try {
                // query case details
                EamCase eamCase = EamDb.getInstance().getCaseDetails(curCaseUUID);

                EamCaseEditDetailsPanel caseInformationPanel = new EamCaseEditDetailsPanel(eamCase);
                caseInformationPanel.addCloseButtonAction((ActionEvent e) -> {
                    popUpWindow.dispose();
                });

                popUpWindow.add(caseInformationPanel);
                popUpWindow.setResizable(true);
                popUpWindow.pack();

                // set the location of the popUp Window on the center of the screen
                Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
                double w = popUpWindow.getSize().getWidth();
                double h = popUpWindow.getSize().getHeight();
                popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

                popUpWindow.setVisible(true);
            } catch (HeadlessException ex) {
                LOGGER.log(Level.WARNING, "Error displaying Enterprise Artifact Manager Case Properties window.", ex); //NON-NLS
            } catch (EamDbException ex) {
                LOGGER.log(Level.WARNING, "Error connecting to Enterprise Artifact Manager databaes.", ex); // NON-NLS
            }
        }
        popUpWindow.setVisible(true);
        popUpWindow.toFront();

    }

    @Override
    public String getName() {
        return Bundle.CTL_EnterpriseArtifactManagerCaseEditCaseInfo();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

}
