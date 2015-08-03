/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.coreutils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class HashHitUtils {

    private static final Logger LOGGER = Logger.getLogger(HashHitUtils.class.getName());

    /**
     * For the given objID, get the names of all the hashsets that the object is
     * in.
     *
     * @param tskCase
     * @param objID   the obj_id to find all the hash sets for
     *
     * @return a set of names, each of which is a hashset that the given object
     *         is in.
     *
     * //TODO: Move this into sleuthkitcase?
     */
    @Nonnull
    static public Set<String> getHashSetNamesForFile(SleuthkitCase tskCase, long objID) {
        try {
            Set<String> hashNames = new HashSet<>();
            List<BlackboardArtifact> arts = tskCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT, objID);

            for (BlackboardArtifact a : arts) {
                List<BlackboardAttribute> attrs = a.getAttributes();
                for (BlackboardAttribute attr : attrs) {
                    if (attr.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        hashNames.add(attr.getValueString());
                    }
                }
            }
            return Collections.unmodifiableSet(hashNames);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "failed to get hash sets for file", ex);
        }
        return Collections.emptySet();
    }

    private HashHitUtils() {
    }
}
