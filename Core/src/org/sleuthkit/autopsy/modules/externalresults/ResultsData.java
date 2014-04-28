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


package org.sleuthkit.autopsy.modules.externalresults;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ResultsData {
    private final List<String> dataSources = new ArrayList<>();
    private final List<ArtifactData> artifacts = new ArrayList<>();
    private final List<ReportData> reports = new ArrayList<>();
    private final List<DerivedFileData> derivedFiles = new ArrayList<>();
    
    public List<String> getDataSources() {
        return dataSources;
    }
    
    public List<ArtifactData> getArtifacts() {
        return artifacts;
    }
    
    public List<ReportData> getReports() {
        return reports;
    }
   
    public List<DerivedFileData> getDerivedFiles() {
        return derivedFiles;
    }    
    
    public void addDataSource(String dataSrc) {
        dataSources.add(dataSrc);
    }
    
    public int addArtifact(String typeStr) {
        ArtifactData d = new ArtifactData();
        d.typeStr = typeStr;
        artifacts.add(d);
        return artifacts.size() - 1;
    }
    
    public int addAttribute(int artIndex, String typeStr) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData d = new AttributeData();
        d.typeStr = typeStr;
        art.attributes.add(d);
        return art.attributes.size() - 1;      
    }
    
    public void addAttributeValue(int artIndex, int attrIndex, String valueStr, String valueType) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);
        attr.valueStr = valueStr;
        if (!valueType.isEmpty()) {
            attr.valueType = valueType;
        }
    }    
    
    public void addAttributeSource(int artIndex, int attrIndex, String source) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);
        attr.source = source;
    }
 
    public void addAttributeContext(int artIndex, int attrIndex, String context) {
        ArtifactData art = artifacts.get(artIndex);
        AttributeData attr = art.attributes.get(attrIndex);
        attr.context = context;
    }    
    
    public int addArtifactFile(int artIndex, String path) {
        ArtifactData art = artifacts.get(artIndex);
        FileData d = new FileData();
        d.path = path;
        art.files.add(d);
        return art.files.size() - 1;      
    }
    
    public void addReport(String displayName, String localPath) {
        ReportData d = new ReportData();
        d.displayName = displayName;
        d.localPath = localPath;
        reports.add(d);
    }
    
    public void addDerivedFile(String localPath) {
        DerivedFileData d = new DerivedFileData();
        d.localPath = localPath;
        derivedFiles.add(d);
    }    
    
    // Data structures
    
    public static class ArtifactData {
        private String typeStr;
        private List<AttributeData> attributes = new ArrayList<>();
        private List<FileData> files = new ArrayList<>();
    }
    
    public static class AttributeData {
        private String typeStr;
        private String valueType = "text"; //default if not specified
        private String valueStr; //valueType determines how to interpret it
        private String source;
        private String context;        
    }    
    
    public static class FileData {
        private String path;   
    }
    
    public static class ReportData {
        private String displayName;
        private String localPath;
    }
        
    public static class DerivedFileData {
        private String localPath;
    }        
}
