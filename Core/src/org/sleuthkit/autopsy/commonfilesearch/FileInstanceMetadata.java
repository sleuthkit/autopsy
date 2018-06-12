/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.io.File;

/**
 * Encapsulates data required to instantiate a <code>FileInstanceNode</code>.
 */
final public class FileInstanceMetadata {
    
    private final Long objectId;
    private final String dataSourceName;
    private final File file;
    
    /**
     * Create meta data required to find an abstract file and build a FileInstanceNode.
     * @param objectId id of abstract file to find
     * @param dataSourceName name of datasource where the object is found
     */
    FileInstanceMetadata(Long objectId, String dataSourceName) {
        this.objectId = objectId;
        this.dataSourceName = dataSourceName;
        this.file = null;
    }
    
    FileInstanceMetadata(Long objectId, String dataSourceName, File file){
        this.objectId = objectId;
        this.dataSourceName = dataSourceName;
        this.file = file;
    }
    
    /**
     * obj_id for the file represented by this object
     * @return 
     */
    public Long getObjectId(){
        return this.objectId;
    }
    
    /**
     * Name of datasource where this instance was found.
     * @return 
     */
    public String getDataSourceName(){
        return this.dataSourceName;
    }
}
