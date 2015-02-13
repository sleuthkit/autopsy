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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import org.netbeans.core.actions.HTMLViewAction;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.HtmlBrowser;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

import java.util.logging.Level;
import java.util.logging.Logger;
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
@Messages("CTL_OnlineHelpAction=Online Documentation")
public final class OnlineHelpAction implements ActionListener {

    private URL url;
    private static final Logger Logger = org.sleuthkit.autopsy.coreutils.Logger.getLogger(AboutWindowPanel.class.getName());

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO implement action body                                   
    try {   
        url = new URL(NbBundle.getMessage(OnlineHelpAction.class, "URL_ON_HELP")); // NOI18N
        showUrl();
    } catch (MalformedURLException ex) {
        Logger.log(Level.SEVERE, "Unable to load Online DOcumentation", ex);
    }
    url = null;
    }
    
        private void showUrl() {
        if (url != null) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(HTMLViewAction.class, "CTL_OpeningBrowser")); //NON-NLS
            HtmlBrowser.URLDisplayer.getDefault().showURL(url);
        }
    }
    
}
