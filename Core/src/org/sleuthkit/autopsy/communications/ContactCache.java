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
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A singleton cache of the Contact artifacts for accounts. This list of
 * TSK_CONTACT artifacts for a given Account retrieved on first access and
 * evicted from the ache after 10 minutes.
 *
 */
final class ContactCache {

    private static final Logger logger = Logger.getLogger(ContactCache.class.getName());

    private final LoadingCache<Account, List<BlackboardArtifact>> accountMap;

    private static ContactCache instance;

    /**
     * Returns the list of Contacts for the given Account.
     *
     * @param account Account instance.
     *
     * @return List of TSK_CONTACT artifacts that references the given Account.
     *         An empty list is returned if no contacts are found.
     *
     * @throws ExecutionException
     */
    static synchronized List<BlackboardArtifact> getContacts(Account account) throws ExecutionException {
        return getInstance().accountMap.get(account);
    }

    /**
     * Force the cache to invalidate all entries.
     */
    static synchronized void invalidateCache() {
        getInstance().accountMap.invalidateAll();
    }

    /**
     * Construct a new instance.
     */
    private ContactCache() {
        accountMap = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).build(
                new CacheLoader<Account, List<BlackboardArtifact>>() {
            @Override
            public List<BlackboardArtifact> load(Account key) {
                try {
                    List<BlackboardArtifact> contactList = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);
                    return findContactForAccount(contactList, key);

                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Failed to load contacts for account %d", key.getAccountID()), ex);
                }
                return new ArrayList<>();
            }
        });

        PropertyChangeListener ingestListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(DATA_ADDED.toString())) {
                ModuleDataEvent eventData = (ModuleDataEvent) pce.getOldValue();
                if (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()) {
                    invalidateCache();
                }
            }
        };

        IngestManager.getInstance().addIngestModuleEventListener(EnumSet.of(DATA_ADDED), ingestListener);
    }

    /**
     * Returns the singleton instance of the cache object.
     *
     * @return AccountCache instance.
     */
    private static synchronized ContactCache getInstance() {
        if (instance == null) {
            instance = new ContactCache();
        }

        return instance;
    }

    /**
     * Returns a list of TSK_CONTACT artifacts that reference the given account.
     *
     * @param allContactList List of existing TSK_CONTACT artifacts.
     * @param account        Account reference.
     *
     * @return A list of TSK_CONTACT artifact that reference the given account
     *         or empty list of none were found.
     *
     * @throws TskCoreException
     */
    private List<BlackboardArtifact> findContactForAccount(List<BlackboardArtifact> allContactList, Account account) throws TskCoreException {
        List<BlackboardArtifact> accountContacts = new ArrayList<>();

        for (BlackboardArtifact contact : allContactList) {
            if (isAccountInAttributeList(contact.getAttributes(), account)) {
                accountContacts.add(contact);
            }
        }

        return accountContacts;
    }

    /**
     * Determine if there is an attribute in the given list that references the
     * given account.
     *
     * @param contactAttributes List of attributes.
     * @param account           Account object.
     *
     * @return True if one of the attributes in the list reference the account.
     */
    private boolean isAccountInAttributeList(List<BlackboardAttribute> contactAttributes, Account account) {
        for (BlackboardAttribute attribute : contactAttributes) {
            if (isAccountInAttribute(attribute, account)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the given attribute to see if it references the given account.
     *
     * @param attribute BlackboardAttribute to check.
     * @param account   References account.
     *
     * @return True if the attribute references the account.
     */
    private boolean isAccountInAttribute(BlackboardAttribute attribute, Account account) {

        String typeName = attribute.getAttributeType().getTypeName();
        return (typeName.startsWith("TSK_EMAIL")
                || typeName.startsWith("TSK_PHONE")
                || typeName.startsWith("TSK_NAME")
                || typeName.startsWith("TSK_ID"))
                && attribute.getValueString().equals(account.getTypeSpecificID());
    }

}
