/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

package org.sleuthkit.autopsy.communications.relationships;

import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.communications.CVTPersonaCache;
import org.sleuthkit.autopsy.communications.ContactCache;
import org.sleuthkit.autopsy.communications.Utils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A set of reusable utility functions for the Relationships package.
 * 
 */
final public class RelationshipsNodeUtilities {
    
    private static final Logger logger = Logger.getLogger(RelationshipsNodeUtilities.class.getName());
    private static final BlackboardAttribute.Type NAME_ATTRIBUTE = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(TSK_NAME.getTypeID()));
    
    
    // Here to make codacy happy
    private RelationshipsNodeUtilities(){
    }
    
     /**
     *
     * Get the display string for the attribute of the given type from the given
     * artifact.
     *
     * @param artifact      the value of artifact
     * @param attributeType the value of TSK_SUBJECT1
     *
     * @return The display string, or an empty string if there is no such
     *         attribute or an an error.
     */
    static String getAttributeDisplayString(final BlackboardArtifact artifact, final BlackboardAttribute.ATTRIBUTE_TYPE attributeType) {
        try {
            BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(attributeType.getTypeID())));
            if (attribute == null) {
                return "";
            } else if (attributeType.getValueType() == DATETIME) {
                return TimeUtilities.epochToTime(attribute.getValueLong(),
                        TimeZone.getTimeZone(Utils.getUserPreferredZoneId()));
            } else {
                return attribute.getDisplayString();
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.WARNING, "Error getting attribute value.", tskCoreException); //NON-NLS
            return "";
        }
    }
    
    @NbBundle.Messages({
        "# {0} - Contact Name",
        "# {1} - Persona Name",
        "RelationshipsNodeUtilities_Tooltip_Template=Contact: {0} - Persona: {1}",
        "# {0} - PersonaAccount count",
        "RelationshipsNodeUtilities_Tooltip_suffix=(1 of {0})"
    })
    static public String getAccoutToolTipText(String displayName, Account account) {
        if(account == null) {
            return displayName;
        }

        List<PersonaAccount> personaList;
        List<BlackboardArtifact> contactArtifactList;
        try {
            personaList = CVTPersonaCache.getPersonaAccounts(account);
            contactArtifactList = ContactCache.getContacts(account);
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "Failed to retrieve Persona details for node.", ex);
            return displayName;
        }

        String personaName;
        if (personaList != null && !personaList.isEmpty()) {
            personaName = personaList.get(0).getPersona().getName();
            if (personaList.size() > 1) {
                personaName += Bundle.RelationshipsNodeUtilities_Tooltip_suffix(Integer.toString(personaList.size()));
            }
        } else {
            personaName = "None";
        }

        String contactName = displayName;
        if (contactArtifactList != null && !contactArtifactList.isEmpty()) {
            try {
                BlackboardArtifact contactArtifact = contactArtifactList.get(0);
                BlackboardAttribute attribute = contactArtifact.getAttribute(NAME_ATTRIBUTE);
                if (attribute != null) {
                    contactName = attribute.getValueString();
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Failed to retrive name attribute from contact artifact.", ex);
            }
        }

        return Bundle.RelationshipsNodeUtilities_Tooltip_Template(contactName, personaName);
    }
    
}
