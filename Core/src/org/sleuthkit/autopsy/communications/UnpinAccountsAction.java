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
 * Action that unpins the AcccountDeviceInstanceKeys in the ActionsGlobalContext
 * form the visualization.
 */
@NbBundle.Messages({"UnpinAccountsAction.pluralText=Remove Selected Accounts",
    "UnpinAccountsAction.singularText=Remove Selected Account"})
final class UnpinAccountsAction extends AbstractCVTAction {

    static final private ImageIcon ICON = ImageUtilities.loadImageIcon(
            "/org/sleuthkit/autopsy/communications/images/marker--minus.png", false);
    private static final String SINGULAR_TEXT = Bundle.UnpinAccountsAction_singularText();
    private static final String PLURAL_TEXT = Bundle.UnpinAccountsAction_pluralText();

    private static final UnpinAccountsAction instance = new UnpinAccountsAction();

    static UnpinAccountsAction getInstance() {
        return instance;
    }

    @Override
    public void actionPerformed(final ActionEvent event) {
        CVTEvents.getCVTEventBus().post(new CVTEvents.UnpinAccountsEvent(getSelectedAccounts()));
    }

    @Override
    String getActionDisplayName() {
        return getSelectedAccounts().size() > 1 ? PLURAL_TEXT : SINGULAR_TEXT;
    }

    @Override
    ImageIcon getIcon() {
        return ICON;
    }
}
