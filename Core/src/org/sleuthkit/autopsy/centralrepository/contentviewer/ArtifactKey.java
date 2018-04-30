/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.util.Objects;

/**
 * Used as a key to ensure we eliminate duplicates from the result set by not overwriting CR correlation instances.
 */
final class ArtifactKey {
    
    private final String dataSource;
    private final String filePath;
    
    ArtifactKey(String theDataSource, String theFilePath) {
        dataSource = theDataSource;
        filePath = theFilePath.toLowerCase();
    }
   
    
    /**
     * 
     * @return the dataSource device ID
     */
    String getDataSource() {
        return dataSource;
    }
    
    /**
     * 
     * @return the filPath including the filename and extension.
     */
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
        //int hash = 7;
        //hash = 67 * hash + this.dataSource.hashCode();
        //hash = 67 * hash + this.filePath.hashCode();
      
        return Objects.hash(dataSource, filePath);
    }
}
