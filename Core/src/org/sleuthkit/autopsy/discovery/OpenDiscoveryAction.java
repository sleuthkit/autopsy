/*
 * Autopsy
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery;

import java.awt.Component;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Class to open the Discovery top component. Allows the user to run searches
 * and see results.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.newpackage.OpenDiscoveryAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 103)
    ,
    @ActionReference(path = "Toolbars/Case", position = 105)})
@ActionRegistration(displayName = "#CTL_OpenDiscoveryAction", lazy = false)
@NbBundle.Messages({"CTL_OpenDiscoveryAction=Discovery"})
public final class OpenDiscoveryAction extends CallableSystemAction implements Presenter.Toolbar {

    private static final Logger logger = Logger.getLogger(OpenDiscoveryAction.class.getName());

    private static final String DISPLAY_NAME = Bundle.CTL_OpenDiscoveryAction();
    private static final long serialVersionUID = 1L;
    private final JButton toolbarButton = new JButton();

    public OpenDiscoveryAction() {
        toolbarButton.addActionListener(OpenDiscoveryAction.this::actionPerformed);
        this.setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @NbBundle.Messages({"OpenDiscoveryAction.resultsIncomplete.text=Results may be incomplete"})

    @Override
    public void performAction() {
        final DiscoveryDialog discDialog = DiscoveryDialog.getDiscoveryDialogInstance();
        discDialog.cancelSearch();
        discDialog.setVisible(true);
        discDialog.displayErrorMessage();
    }

    /**
     * Returns the toolbar component of this action.
     *
     * @return The toolbar button
     */
    @Override
    public Component getToolbarPresenter() {
        ImageIcon icon = new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/discovery-icon-24.png")); //NON-NLS
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
    public String getName() {
        return DISPLAY_NAME;
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
