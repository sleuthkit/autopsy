/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;

class CaseDataSourcesWrapper {

    private final CorrelationCase eamCase;
    private final List<CorrelationDataSource> dataSources;

    CaseDataSourcesWrapper(CorrelationCase correlationCase, List<CorrelationDataSource> dataSourceList) {
        eamCase = correlationCase;
        dataSources = dataSourceList;
    }

    String getDisplayName() {
        return eamCase.getDisplayName();
    }

    List<CorrelationDataSource> getDataSources() {
        return Collections.unmodifiableList(dataSources);
    }

    String getCreationDate() {
        return eamCase.getCreationDate();
    }

    String getOrganizationName() {
        EamOrganization org = eamCase.getOrg();
        return org == null ? "" : org.getName();
    }

    String getCaseNumber() {
        return eamCase.getCaseNumber();
    }

    String getExaminerName() {
        return eamCase.getExaminerName();
    }

    String getExaminerEmail() {
        return eamCase.getExaminerEmail();
    }

    String getNotes() {
        return eamCase.getNotes();
    }

    String getExaminerPhone() {
        return eamCase.getExaminerPhone();
    }
}
