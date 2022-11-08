/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;

/**
 * A singleton cache of the PersonaAccount information. The list of
 * PersonaAccounts for a given Account typeSpecificID retrieved on first access
 * and evicted from the cache after 5 minutes.
 */
final public class CVTPersonaCache {

    private static final Logger logger = Logger.getLogger(CVTPersonaCache.class.getName());
    private final LoadingCache<Account, List<PersonaAccount>> accountMap;

    private static CVTPersonaCache instance;

    /**
     * Cache constructor.
     */
    private CVTPersonaCache() {
        accountMap = CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build(
                new CacheLoader<Account, List<PersonaAccount>>() {
            @Override
            public List<PersonaAccount> load(Account key) {
                List<PersonaAccount> accountList = new ArrayList<>();
                try {
                    if (CentralRepository.isEnabled()) {
                        Collection<PersonaAccount> accounts = PersonaAccount.getPersonaAccountsForAccount(key);
                        accountList.addAll(accounts);
                    }
                } catch (CentralRepoException ex) {
                    logger.log(Level.WARNING, String.format("Unable to load Persona information for account: %s", key), ex);
                }
                return accountList;
            }
        }
        );
    }

    /**
     * Returns the singleton instance of the cache.
     *
     * @return CVTPersonaCache instance.
     */
    private static synchronized CVTPersonaCache getInstance() {
        if (instance == null) {
            instance = new CVTPersonaCache();
        }

        return instance;
    }

    /**
     * Returns the list of PersonaAccounts for the given Account.
     *
     * @param account The account.
     *
     * @return List of PersonaAccounts for id or empty list if none were found.
     *
     * @throws ExecutionException
     */
    static public synchronized List<PersonaAccount> getPersonaAccounts(Account account) throws ExecutionException {
        return getInstance().accountMap.get(account);
    }
}
