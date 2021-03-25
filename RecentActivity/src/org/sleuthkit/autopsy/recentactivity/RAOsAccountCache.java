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
package org.sleuthkit.autopsy.recentactivity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Cache of OsAccounts for a given host to be used by the various Recent
 * Activity Extractors.
 *
 */
final class RAOsAccountCache {

    private final Map<String, OsAccount> accountCache = new HashMap<>();

    /**
     * initialize the account map for the given host. This should be done after
     * the ExtractRegistry is run.
     *
     * @param tskCase
     * @param host
     *
     * @throws TskCoreException
     */
    void initialize(SleuthkitCase tskCase, Host host) throws TskCoreException {
        buildAccountMap(tskCase, host);
    }

    /**
     * Returns the appropriate OsAccount for the given file.
     *
     * If the file is not associated with an OsAccount, try to find one based on
     * the location of the file.
     *
     * If the file is associated with the system account of S-1-5-32-544 use the
     * file path to determine which user account to associate the file with.
     *
     *
     * @param file The file to match with appropriate OsAccount.
     *
     * @return Optional OsAccount, may not be present if one is not found.
     *
     * @throws TskCoreException
     */
    Optional<OsAccount> getOsAccount(AbstractFile file) throws TskCoreException {
        Optional<Long> optional = file.getOsAccountObjectId();

        if (!optional.isPresent()) {
            return getAccountForPath(file.getParentPath());
        }

        OsAccount osAccount = Case.getCurrentCase().getSleuthkitCase().getOsAccountManager().getOsAccount(optional.get());
        if (osAccount.getName().equals("S-1-5-32-544")) {
            return getAccountForPath(file.getParentPath());
        }

        return Optional.ofNullable(osAccount);
    }

    /**
     * Return a user account if the given path's parent directory is a user
     * account home directory.
     *
     * @param path Path to search.
     *
     * @return An Optional OsAccount if one was found.
     */
    private Optional<OsAccount> getAccountForPath(String path) {
        Path filePath = Paths.get(path.toLowerCase());
        // Check if the path might be a user path.
        if (filePath.startsWith(Paths.get("/users")) || filePath.startsWith("/document and settings")) {
            for (String key : accountCache.keySet()) {
                if (filePath.startsWith(Paths.get(key))) {
                    return Optional.of(accountCache.get(key));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Build a map of user home directories to OsAccounts for the given host.
     *
     * @throws TskCoreException
     */
    private void buildAccountMap(SleuthkitCase tskCase, Host host) throws TskCoreException {
        BlackboardAttribute.Type homeDir = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HOME_DIR);
        List<OsAccount> accounts = tskCase.getOsAccountManager().getAccounts(host);

        for (OsAccount account : accounts) {
            List<OsAccountAttribute> attributeList = account.getOsAccountAttributes();

            for (OsAccountAttribute attribute : attributeList) {
                if (attribute.getHostId().isPresent()
                        && attribute.getHostId().get().equals(host.getId())
                        && attribute.getAttributeType().equals(homeDir)) {
                    accountCache.put(attribute.getValueString(), account);
                }
            }
        }
    }
}
