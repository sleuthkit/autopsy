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
import java.util.Collections;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Associate a host with a particular (existing) person.
 */
@Messages({
    "AssociatePersonAction_unknownPerson=Unknown Person",
    "AssociatePersonAction_onError_title=Error Associating Host with Person",
    "# {0} - hostName",
    "# {1} - personName",
    "AssociatePersonAction_onError_description=There was an error associating host {0} with person {1}.",})
public class AssociatePersonAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(AssociatePersonAction.class.getName());

    private final Host host;
    private final Person person;

    /**
     * Main constructor.
     *
     * @param host The host that will get associated with the person.
     * @param person The person to be the parent of the host.
     */
    public AssociatePersonAction(Host host, Person person) {
        super(person == null || person.getName() == null
                ? Bundle.RemoveParentPersonAction_unknownPerson()
                : person.getName());

        this.host = host;
        this.person = person;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            Case.getCurrentCaseThrows().getSleuthkitCase().getPersonManager().addHostsToPerson(person, Collections.singletonList(host));
        } catch (NoCurrentCaseException | TskCoreException ex) {
            String hostName = this.host == null || this.host.getName() == null ? "" : this.host.getName();
            String personName = this.person == null || this.person.getName() == null ? "" : this.person.getName();
            logger.log(Level.WARNING, String.format("Unable to remove parent from host: %s with person: %s", hostName, personName), ex);

            JOptionPane.showMessageDialog(
                    WindowManager.getDefault().getMainWindow(),
                    Bundle.AssociatePersonAction_onError_description(hostName, personName),
                    Bundle.AssociatePersonAction_onError_title(),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

}
