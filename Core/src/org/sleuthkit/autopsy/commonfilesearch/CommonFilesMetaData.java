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

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility and wrapper around data required for Common Files Search results.
 * Subclass this to implement different selections of files from the case.
 */
public class CommonFilesMetaData {
        
    private final String parentMd5;
    private final List<AbstractFile> children;
    private final String dataSources;
    private final Map<Long, String> dataSourceIdToNameMap;

    CommonFilesMetaData(String md5, List<AbstractFile> childNodes, String dataSourcesString, Map<Long,String> dataSourcesMap) throws TskCoreException, SQLException, NoCurrentCaseException {
        parentMd5 = md5;
        children = childNodes;
        dataSources = dataSourcesString;
        dataSourceIdToNameMap = dataSourcesMap;
    }

    public String getMd5() {
        return parentMd5;
    }
    
    public List<AbstractFile> getChildren() {
        return Collections.unmodifiableList(this.children);
    }

    public Map<Long, String> getDataSourceIdToNameMap() {
        return Collections.unmodifiableMap(dataSourceIdToNameMap);
    }

    public String getDataSources() {
        return dataSources;
    }

}
