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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import org.sleuthkit.datamodel.TskData;


/**
 * Common Files Search usage which extends CorrelationAttributeInstance
 * by adding the MD5 value to match on for the results table.
 */
public class CentralRepositoryFile extends CorrelationAttributeInstance {

    private static final long serialVersionUID = 1L;
    
    /**
     * The common MD5 value
     */
    private final String value;
    
    public CentralRepositoryFile(CorrelationCase eamCase, CorrelationDataSource eamDataSource, String filePath, String comment, TskData.FileKnown knownStatus, String value) throws EamDbException {
        super(eamCase, eamDataSource, filePath, comment, knownStatus);
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }    
}
