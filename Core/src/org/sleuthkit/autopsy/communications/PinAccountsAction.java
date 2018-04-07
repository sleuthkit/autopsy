/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;

/**
 * Action that pins the AccountDevicesIntanceKeys in the ActionsGlobalContext to
 * the visualizaion
 */
@NbBundle.Messages({"PinAccountsAction.pluralText=Add Selected Accounts to Visualization",
    "PinAccountsAction.singularText=Add Selected Account to Visualization"})
final class PinAccountsAction extends AbstractCVTAction {

    static private final ImageIcon ICON = ImageUtilities.loadImageIcon(
            "/org/sleuthkit/autopsy/communications/images/marker--plus.png", false);
    private static final String SINGULAR_TEXT = Bundle.PinAccountsAction_singularText();
    private static final String PLURAL_TEXT = Bundle.PinAccountsAction_pluralText();

    private static final PinAccountsAction instance = new PinAccountsAction();

    static PinAccountsAction getInstance() {
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        CVTEvents.getCVTEventBus().post(new CVTEvents.PinAccountsEvent(getSelectedAccounts(), false));
    }

    @Override
    protected String getActionDisplayName() {
        return getSelectedAccounts().size() > 1 ? PLURAL_TEXT : SINGULAR_TEXT;
    }

    @Override
    ImageIcon getIcon() {
        return ICON;
    }
}
