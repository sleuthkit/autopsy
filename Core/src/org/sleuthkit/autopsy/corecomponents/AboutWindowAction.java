/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Dialog;
import org.netbeans.core.actions.AboutAction;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * Action to open custom implementation of the "About" window from the Help
 * menu.
 */
@ActionID(id = "org.sleuthkit.autopsy.corecomponents.AboutWindowAction", category = "Help")
@ActionRegistration(displayName = "#CTL_CustomAboutAction", iconInMenu = true, lazy = false)
@ActionReference(path = "Menu/Help", position = 3000, separatorBefore = 2999)
public class AboutWindowAction extends AboutAction {

    @Override
    public void performAction() {
        AboutWindowPanel pip = new AboutWindowPanel("org/sleuthkit/autopsy/images/splash.png");
        DialogDescriptor descriptor = new DialogDescriptor(
                pip,
                NbBundle.getMessage(AboutWindowAction.class, "CTL_CustomAboutAction"),
                true,
                new Object[0],
                null,
                DialogDescriptor.DEFAULT_ALIGN,
                null,
                null);
        Dialog dlg = null;
        try {
            dlg = DialogDisplayer.getDefault().createDialog(descriptor);
            dlg.setResizable(true);
            dlg.setVisible(true);
        } finally {
            if (dlg != null) {
                dlg.dispose();
            }
        }
    }
}
