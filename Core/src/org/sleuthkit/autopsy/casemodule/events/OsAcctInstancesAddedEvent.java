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
package org.sleuthkit.autopsy.casemodule.events;

import java.util.List;
import static org.sleuthkit.autopsy.casemodule.Case.Events.OS_ACCT_INSTANCES_ADDED;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An application event published when OS account instances are added to the
 * Sleuth Kit data model for a case.
 */
public final class OsAcctInstancesAddedEvent extends TskDataModelChangedEvent<OsAccountInstance, OsAccountInstance> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an application event published when OS account instances are
     * added to the Sleuth Kit data model for a case.
     *
     * @param osAcctInstances The OS account instances that were added.
     */
    public OsAcctInstancesAddedEvent(List<OsAccountInstance> osAcctInstances) {
        super(OS_ACCT_INSTANCES_ADDED.toString(), null, null, osAcctInstances, OsAccountInstance::getInstanceId);
    }

    /**
     * Gets the OS account instances that have been added.
     *
     * @return The OS account instances.
     */
    public List<OsAccountInstance> getOsAccountInstances() {
        return getNewValue();
    }

    @Override
    protected List<OsAccountInstance> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        return caseDb.getOsAccountManager().getOsAccountInstances(ids);
    }

}
