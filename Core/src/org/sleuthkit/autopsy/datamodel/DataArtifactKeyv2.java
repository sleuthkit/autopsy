/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 * @author gregd
 */
public class DataArtifactKeyv2 {
    private final BlackboardArtifact.Type artifactType;
    private final Long dataSourceId;

    public DataArtifactKeyv2(BlackboardArtifact.Type artifactType, Long dataSourceId) {
        this.artifactType = artifactType;
        this.dataSourceId = dataSourceId;
    }

    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }
}
