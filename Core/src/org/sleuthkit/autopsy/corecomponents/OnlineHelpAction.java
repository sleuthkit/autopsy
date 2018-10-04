/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Desktop;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URI;
import org.netbeans.core.actions.HTMLViewAction;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Implements a hyperlink to the Online Documentation.
 */
@ActionID(
        category = "Help",
        id = "org.sleuthkit.autopsy.corecomponents.OnlineHelpAction"
)
@ActionRegistration(
        displayName = "#CTL_OnlineHelpAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/Help", position = 0),
    @ActionReference(path = "Shortcuts", name = "F1")
})
@Messages("CTL_OnlineHelpAction=Online Autopsy Documentation")
public final class OnlineHelpAction implements ActionListener {

    private URI uri;
    private static final Logger Logger = org.sleuthkit.autopsy.coreutils.Logger.getLogger(AboutWindowPanel.class.getName());

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO implement action body                                   
        viewOnlineHelp();
    }

    /**
     * Displays the Online Documentation in the system browser. If not
     * available, displays it in the built-in OpenIDE HTML Browser.
     */
    private void viewOnlineHelp() {
        try {
            uri = new URI("http://sleuthkit.org/autopsy/docs/user-docs/" + Version.getVersion() + "/");
        } catch (URISyntaxException ex) {
            Logger.log(Level.SEVERE, "Unable to load Online Documentation", ex); //NON-NLS
        }
        if (uri != null) {
            // Display URL in the System browser
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.browse(uri);
                } catch (IOException ex) {
                    // TODO Auto-generated catch block
                    Logger.log(Level.SEVERE, "Unable to launch the system browser", ex); //NON-NLS
                }
            } else {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(HTMLViewAction.class, "CTL_OpeningBrowser")); //NON-NLS
                try {
                    HtmlBrowser.URLDisplayer.getDefault().showURL(uri.toURL());
                } catch (MalformedURLException ex) {
                    Logger.log(Level.SEVERE, "Unable to launch the built-in browser", ex); //NON-NLS
                }
            }
        }
    }

}
