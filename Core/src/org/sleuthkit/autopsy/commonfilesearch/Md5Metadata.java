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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates data required to instantiate an <code>Md5Node</code>.
 */
final public class Md5Metadata {
    
    private final String md5;
    private final List<FileInstanceMetadata> fileInstances;
    
    Md5Metadata(String md5, List<FileInstanceMetadata> fileInstances){
        this.md5 = md5;
        this.fileInstances = fileInstances;

    }
    
    public String getMd5(){
        return this.md5;
    }
    
    void addFileInstanceMetadata(FileInstanceMetadata metadata){
        this.fileInstances.add(metadata);
    }
    
    public Collection<FileInstanceMetadata> getMetadata(){
        return Collections.unmodifiableCollection(this.fileInstances);
    }
    
    /**
     * How many distinct file instances exist for the MD5 represented by this object?
     * @return number of instances
     */
    public int size(){
        return this.fileInstances.size();
    }

    public String getDataSources() {
        Set<String> sources = new HashSet<> ();
        for(FileInstanceMetadata data  : this.fileInstances){
            sources.add(data.getDataSourceName());
        }
        return String.join(", ", sources);
    }
    
    

}
