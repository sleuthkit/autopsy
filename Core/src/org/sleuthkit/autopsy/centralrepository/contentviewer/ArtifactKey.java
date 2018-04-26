/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.util.Objects;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 *
 * @author Andrrew
 */
final class ArtifactKey {
    
    private final String dataSource;
    private final String filePath;
    
    ArtifactKey(String theDataSource, String theFilePath) {
        dataSource = theDataSource;
        filePath = theFilePath;
    }
    
    ArtifactKey(CorrelationAttributeInstance instance) {
        dataSource = instance.getCorrelationDataSource().getDeviceID();
        filePath = instance.getFilePath();
    }
    
    String getDataSource() {
        return dataSource;
    }
    
    String getFilePath() {
        return filePath;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof ArtifactKey) {
            return ((ArtifactKey) other).getDataSource().equals(dataSource) && ((ArtifactKey) other).getFilePath().equals(filePath);
        }
        return false;
        
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.dataSource);
        hash = 67 * hash + Objects.hashCode(this.filePath);
        return hash;
    }
}
