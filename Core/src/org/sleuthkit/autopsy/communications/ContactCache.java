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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * A singleton cache of the Contact artifacts for accounts. The map of account
 * unique ids to list of contact artifacts is stored in a LoadingCache which
 * expires after 10 of non-use.
 *
 */
final public class ContactCache {

    private static final Logger logger = Logger.getLogger(ContactCache.class.getName());

    private static ContactCache instance;

    private final LoadingCache<String, Map<String, List<BlackboardArtifact>>> accountMap;

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
    static public synchronized List<BlackboardArtifact> getContacts(Account account) throws ExecutionException {
        return getInstance().accountMap.get("realMap").get(account.getTypeSpecificID());
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
                new CacheLoader<String, Map<String, List<BlackboardArtifact>>>() {
            @Override
            public Map<String, List<BlackboardArtifact>> load(String key) {
                try {
                    return buildMap();
                } catch (SQLException | TskCoreException ex) {
                    logger.log(Level.WARNING, "Failed to build account to contact map", ex);
                }
                return new HashMap<>();  // Return an empty map if there is an exception to avoid NPE and continual trying.
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

        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent event) -> {
            if (event.getNewValue() == null) {
                invalidateCache();
            }
        });

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
     * Builds the map of account IDs to contacts that reference them.
     *
     * @return A map of account IDs to contact artifacts.
     *
     * @throws TskCoreException
     * @throws SQLException
     */
    private Map<String, List<BlackboardArtifact>> buildMap() throws TskCoreException, SQLException {
        Map<String, List<BlackboardArtifact>> acctMap = new HashMap<>();
        List<BlackboardArtifact> contactList = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT);

        for (BlackboardArtifact contactArtifact : contactList) {
            List<BlackboardAttribute> contactAttributes = contactArtifact.getAttributes();
            for (BlackboardAttribute attribute : contactAttributes) {
                String typeName = attribute.getAttributeType().getTypeName();

                if (typeName.startsWith("TSK_EMAIL")
                        || typeName.startsWith("TSK_PHONE")
                        || typeName.startsWith("TSK_NAME")
                        || typeName.startsWith("TSK_ID")) {
                    String accountID = attribute.getValueString();
                    List<BlackboardArtifact> artifactList = acctMap.getOrDefault(accountID, new ArrayList<>());

                    acctMap.put(accountID, artifactList);

                    if (!artifactList.contains(contactArtifact)) {
                        artifactList.add(contactArtifact);
                    }
                }
            }

        }

        return acctMap;
    }
}
