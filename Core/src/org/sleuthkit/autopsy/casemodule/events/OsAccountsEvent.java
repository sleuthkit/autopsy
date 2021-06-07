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

import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A base class for application events published when OS accounts in the Sleuth
 * Kit data model for a case have been added or updated.
 */
class OsAccountsEvent extends TskDataModelChangedEvent<OsAccount, OsAccount> {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the base class part of an application event published when
     * OS accounts in the Sleuth Kit data model for a case have been added or
     * updated.
     *
     * @param eventName The name of the Case.Events enum value for the event
     *                  type.
     * @param account   The OS accounts.
     */
    OsAccountsEvent(String eventName, List<OsAccount> osAccounts) {
        super(eventName, null, null, osAccounts, OsAccount::getId);
    }

    /**
     * Gets the OS accounts that have been added or updated.
     *
     * @return The OS accounts.
     */
    public List<OsAccount> getOsAccounts() {
        return getNewValue();
    }

    @Override
    protected List<OsAccount> getNewValueObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        List<OsAccount> osAccounts = new ArrayList<>();
        for (Long id : ids) {
            osAccounts.add(caseDb.getOsAccountManager().getOsAccountByObjectId(id));
        }
        return osAccounts;
    }

}
