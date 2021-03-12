/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Menu action to merge a host into another host.
 */
@Messages({
    "MergeHostAction_onError_title=Error Merging Hosts",
    "# {0} - sourceHostName",
    "# {1} - destHostName",
    "MergeHostAction_onError_description=There was an error merging host {0} into host {1}.",})
public class MergeHostAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(MergeHostAction.class.getName());

    private final Host sourceHost;
    private final Host destHost;

    /**
     * Main constructor.
     *
     * @param sourceHost The source host.
     * @param destHost   The destination host. 
     */
    public MergeHostAction(Host sourceHost, Host destHost) {
        super(destHost.getName());

        this.sourceHost = sourceHost;
        this.destHost = destHost;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Case.getCurrentCaseThrows().getSleuthkitCase().getHostManager().mergeHosts(sourceHost, destHost);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to merge host: %s into host: %s", sourceHost.getName(), destHost.getName()), ex);

            JOptionPane.showMessageDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.MergeHostAction_onError_description(sourceHost, destHost),
                    Bundle.MergeHostAction_onError_title(),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

}
