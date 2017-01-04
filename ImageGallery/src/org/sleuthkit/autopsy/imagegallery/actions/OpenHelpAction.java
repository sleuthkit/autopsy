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

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;

@ActionID(
        category = "Help",
        id = "org.sleuthkit.autopsy.imagegallery.actions.OpenHelpAction"
)
@ActionRegistration(
        displayName = "#CTL_OpenHelpAction"
)
@ActionReference(path = "Menu/Help", position = 350)
@Messages("CTL_OpenHelpAction=Image / Video Gallery Help")
public final class OpenHelpAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Desktop.getDesktop().browse(URI.create("http://sleuthkit.org/autopsy/docs/user-docs/4.3/image_gallery_page.html")); //NON-NLS
        } catch (IOException ex) {
            Logger.getLogger(OpenHelpAction.class.getName()).log(Level.SEVERE, "failed to open help page", ex); //NON-NLS
        }
    }
}
