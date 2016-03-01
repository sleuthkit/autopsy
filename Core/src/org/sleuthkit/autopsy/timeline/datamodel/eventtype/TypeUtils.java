/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel.eventtype;

import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 */
public class TypeUtils {

   

     static BlackboardArtifact.Type fromEnum(BlackboardArtifact.ARTIFACT_TYPE type) {
        return new BlackboardArtifact.Type(type.getTypeID(), type.getLabel(), type.getDisplayName());
    }

    private TypeUtils() {
    }
}
