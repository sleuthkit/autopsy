/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

package org.sleuthkit.autopsy.externalresults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 */
final class ExternalResults {
    private final List<String> dataSources = new ArrayList<>();
    private final List<ArtifactData> artifacts = new ArrayList<>();
    private final List<ReportData> reports = new ArrayList<>();
    private final List<DerivedFileData> derivedFiles = new ArrayList<>();
    
    List<String> getDataSources() {
        return dataSources;
    }
    
    List<ArtifactData> getArtifacts() {
        return artifacts;
    }
    
    List<ReportData> getReports() {
        return reports;
    }
   
    List<DerivedFileData> getDerivedFiles() {
        return derivedFiles;
    }    
    
    void addDataSource(String dataSrc) {
        dataSources.add(dataSrc);
    }
    
    int addArtifact(String typeStr) {
        ArtifactData d = new ArtifactData();
        d.typeStr = typeStr;
        artifacts.add(d);
        return artifacts.size() - 1;
    }
    
    int addAttribute(int artIndex, String typeStr) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData d = new AttributeData();
        d.typeStr = typeStr;
        art.attributes.add(d);
        return art.attributes.size() - 1;      
    }
    
    void addAttributeValue(int artIndex, int attrIndex, String valueStr, String valueType) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);        
        if (valueType.isEmpty()) {
            valueType = AttributeData.DEFAULT_VALUE_TYPE;
        }
        attr.valueStr.put(valueType, valueStr);
    }    
    
    void addAttributeSource(int artIndex, int attrIndex, String source) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);
        attr.source = source;
    }
 
    void addAttributeContext(int artIndex, int attrIndex, String context) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);
        attr.context = context;
    }    
    
    int addArtifactFile(int artIndex, String path) {
        ArtifactData art = artifacts.get(artIndex);
        FileData d = new FileData();
        d.path = path;
        art.files.add(d);
        return art.files.size() - 1;      
    }
    
    void addReport(String name, String displayName, String localPath) {
        ReportData d = new ReportData();
        d.name = name;
        d.displayName = displayName;
        d.localPath = localPath;
        reports.add(d);
    }
    
    void addDerivedFile(String localPath, String parentPath) {
        DerivedFileData d = new DerivedFileData();
        d.localPath = localPath;
        d.parentPath = parentPath;
        derivedFiles.add(d);
    }    
    
    // Data structures
    
    static class ArtifactData {
        public String typeStr;
        public List<AttributeData> attributes = new ArrayList<>();
        public List<FileData> files = new ArrayList<>();
    }
    
    static class AttributeData {
        public static final String DEFAULT_VALUE_TYPE = "text";
        public String typeStr;        
        public HashMap<String, String> valueStr = new HashMap<>(); //valueType determines how to interpret valueStr
        public String source;
        public String context;        
    }    
    
    static class FileData {
        public String path;   
    }
    
    static class ReportData {
        public String name;
        public String displayName;
        public String localPath;
    }
        
    static class DerivedFileData {
        public String localPath;
        public String parentPath;
    }        
}
