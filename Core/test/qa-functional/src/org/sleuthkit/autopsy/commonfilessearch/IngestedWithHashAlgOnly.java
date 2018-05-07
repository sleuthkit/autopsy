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
package org.sleuthkit.autopsy.commonfilessearch;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.openide.util.Exceptions;
import org.python.icu.impl.Assert;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.commonfilesearch.AllDataSourcesCommonFilesAlgorithm;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadata;
import org.sleuthkit.autopsy.commonfilesearch.CommonFilesMetadataBuilder;
import org.sleuthkit.autopsy.commonfilesearch.FileInstanceMetadata;
import org.sleuthkit.autopsy.commonfilesearch.Md5Metadata;
import org.sleuthkit.autopsy.commonfilesearch.SingleDataSource;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettings.IngestType;
import org.sleuthkit.autopsy.ingest.IngestModuleTemplate;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.autopsy.testutils.IngestUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

public class IngestedWithHashAlgOnly extends IntraCaseCommonFilesSearchTest {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(IngestedWithHashAlgOnly.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public IngestedWithHashAlgOnly(String name) {
        super(name);
    }

    @Override
    public void setUp() {
        super.setUp();

        IngestModuleTemplate hashLookupTemplate = IngestUtils.getIngestModuleTemplate(new HashLookupModuleFactory());

        ArrayList<IngestModuleTemplate> templates = new ArrayList<>();
        templates.add(hashLookupTemplate);

        IngestJobSettings ingestJobSettings = new IngestJobSettings(IngestedWithHashAlgOnly.class.getCanonicalName(), IngestType.FILES_ONLY, templates);

        try {
            IngestUtils.runIngestJob(Case.getOpenCase().getDataSources(), ingestJobSettings);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    /**
     * Add #1, #2, #3, and #4 to case and ingest with hash algorithm. Find all
     * matches & all file types. Confirm file.jpg is found on all three and
     * file.docx is found on two. Find matches on ‘#1’ & all file types. Confirm
     * same results. Find matches on ‘#2 & all file types: Confirm file.jpg.
     * Find matches on ‘#3’ & all file types: Confirm file.jpg and file.docx.
     * Find matches on #4 & all file types: Confirm nothing is found
     */
    public void testOne() {
        try {
            Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();

            CommonFilesMetadataBuilder allSourcesBuilder = new AllDataSourcesCommonFilesAlgorithm(dataSources, false, false);
            CommonFilesMetadata metadata = allSourcesBuilder.findCommonFiles();
            
            Map<Long, String> objectIdToDataSource = mapFileInstancesToDataSources(metadata);
            
            List<AbstractFile> files = getFiles(objectIdToDataSource.keySet());
            
            

        } catch (NoCurrentCaseException ex) {
            Exceptions.printStackTrace(ex);
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        } catch (SQLException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    private boolean fileExists(List<AbstractFile> files, String name, String dataSource, int count){
        for (AbstractFile file : files){
                
                Long id = file.getId();
                
                String fileName = file.getName();
                
                final String dataSourceName = objectIdToDataSource.get(id);
                
                switch(fileName){
                    case "IMG_6175.jpg":
                        
                    case "BasicStyleGuide.doc":
                    case "asdf.pdf":
                    case "file.dat":
                        
                }
            }
    }
    
    private boolean fileExists(List<AbstractFile> files, String name, String dataSource){
        return fileExists(files, name, dataSource, 1);
    }

    public void testTwo() {
        try {
            Map<Long, String> dataSources = this.dataSourceLoader.getDataSourceMap();
            Long first = new Long(1);

            CommonFilesMetadataBuilder singleSourceBuilder = new SingleDataSource(first, dataSources, false, false);
            CommonFilesMetadata metadata = singleSourceBuilder.findCommonFiles();

            

        } catch (NoCurrentCaseException | TskCoreException | SQLException ex) {
            Exceptions.printStackTrace(ex);
            Assert.fail(ex);
        }
    }

    private Map<Long, String> mapFileInstancesToDataSources(CommonFilesMetadata metadata) {
        Map<Long, String> instanceIdToDataSource = new HashMap<>();
        
        for(Entry<String, Md5Metadata> entry : metadata.getMetadata().entrySet()){
            for (FileInstanceMetadata md : entry.getValue().getMetadata()){
                instanceIdToDataSource.put(md.getObjectId(), md.getDataSourceName());
            }
        }
        
        return instanceIdToDataSource;
    }

    private List<AbstractFile> getFiles(Set<Long> keySet) {
        List<AbstractFile> files = new ArrayList<>(keySet.size());
        
        for(Long id : keySet){
            try {
                AbstractFile file = Case.getOpenCase().getSleuthkitCase().getAbstractFileById(id);
                files.add(file);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                Exceptions.printStackTrace(ex);
                Assert.fail(ex);
            }            
        }
        
        return files;
    }
}
