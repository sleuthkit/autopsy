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

import org.sleuthkit.autopsy.coreutils.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Paths;
import org.netbeans.core.actions.HTMLViewAction;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Implements a hyperlink to the Offline Documentation.
 */
@ActionID(
        category = "Help",
        id = "org.sleuthkit.autopsy.corecomponents.OfflineHelpAction"
)
@ActionRegistration(
        displayName = "#CTL_OfflineHelpAction"
)
@ActionReferences({
    @ActionReference(path = "Menu/Help", position = 1),
    @ActionReference(path = "Shortcuts", name = "F2")
})
@Messages("CTL_OfflineHelpAction=Offline Autopsy Documentation")
public final class OfflineHelpAction implements ActionListener {

    private static final String DOCS_FOLDER = "docs";
    private static final String HELP_HTML_FILE = "index.html";

    private static final Logger logger
            = org.sleuthkit.autopsy.coreutils.Logger.getLogger(AboutWindowPanel.class.getName());

    @Override
    public void actionPerformed(ActionEvent e) {
        viewOfflineHelp();
    }

    /**
     * Displays the Offline Documentation in the system browser. If not
     * available, displays it in the built-in OpenIDE HTML Browser.
     *
     * Tested and working: Chrome, Firefox, IE Not tested: Opera, Safari
     */
    private void viewOfflineHelp() {

        File systemHelpFile = getOfflineHelpFile();
        if (systemHelpFile == null) {
            logger.log(Level.SEVERE, "Unable to load Offline Documentation file");
            return;
        }

        // Display URL in the System browser
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.open(systemHelpFile);
                return;
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Unable to launch the system browser: "
                        + systemHelpFile, ex); //NON-NLS
            }
        }

        org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                NbBundle.getMessage(HTMLViewAction.class, "CTL_OpeningBrowser")); //NON-NLS
        try {
            HtmlBrowser.URLDisplayer.getDefault().showURL(systemHelpFile.toURI().toURL());
        } catch (MalformedURLException ex) {
            logger.log(Level.SEVERE, "Unable to launch the built-in browser: "
                    + systemHelpFile, ex); //NON-NLS
        }

    }

    private File getOfflineHelpFile() {
        return InstalledFileLocator.getDefault().getDefault().locate(
                Paths.get(DOCS_FOLDER, HELP_HTML_FILE).toString(),
                OfflineHelpAction.class.getPackage().getName(),
                false);
    }
}
