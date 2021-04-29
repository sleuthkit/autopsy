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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *  Parent class for specific OsAccount event classes.
 */
class OsAccountsEvent extends TskDataModelChangedEvent<OsAccount> {

    private static final long serialVersionUID = 1L;
    
    /**
     * Construct a new OsAccountEvent.
     * 
     * @param eventName The name of the event.
     * @param account   The OsAccount the event applies to.
     */
    OsAccountsEvent(String eventName, List<OsAccount> accounts) {
        super(eventName, accounts, OsAccount::getId);
    }
    
    /**
     * Returns the OsAccount that changed.
     * 
     * @return The OsAccount that was changed.
     */
    public OsAccount getOsAccount() {
        List<OsAccount> accounts = getNewValue();
        return accounts.get(0);
    }

    @Override
    protected List<OsAccount> getDataModelObjects(SleuthkitCase caseDb, List<Long> ids) throws TskCoreException {
        Long id = ids.get(0);
        OsAccount account = caseDb.getOsAccountManager().getOsAccountByObjectId(id);
        List<OsAccount> accounts = new ArrayList<>();
        accounts.add(account);
        return accounts;
    } 
}
