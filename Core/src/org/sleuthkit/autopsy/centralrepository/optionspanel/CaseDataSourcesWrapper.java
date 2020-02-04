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
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoOrganization;

/**
 * An object to contain both a CorrelationCase and the list of
 * CorrelationDataSources which are associated with that case.
 */
class CaseDataSourcesWrapper {

    private final CorrelationCase eamCase;
    private final List<CorrelationDataSource> dataSources;

    /**
     * Create a new CaseDataSourcesWrapper object.
     *
     * @param correlationCase - the CorrelationCase which is being represented
     * @param dataSourceList  - the list of CorrelationDataSource objects which
     *                        are associated with the CorrelationCase
     */
    CaseDataSourcesWrapper(CorrelationCase correlationCase, List<CorrelationDataSource> dataSourceList) {
        eamCase = correlationCase;
        dataSources = dataSourceList;
    }

    /**
     * Get the display name of the CorrelationCase.
     *
     * @return the display name of the CorrelationCase.
     */
    String getDisplayName() {
        return eamCase.getDisplayName();
    }

    /**
     * Get the list of CorrelationDataSources associated with the
     * CorrelationCase.
     *
     * @return the list of CorrelationDataSources associated with the
     *         CorrelationCase.
     */
    List<CorrelationDataSource> getDataSources() {
        return Collections.unmodifiableList(dataSources);
    }

    /**
     * Get the creation date of the CorrelationCase.
     *
     * @return the creation date of the CorrelationCase.
     */
    String getCreationDate() {
        return eamCase.getCreationDate();
    }

    /**
     * Get the organization name of the CorrelationCase.
     *
     * @return the organization name of the CorrelationCase.
     */
    String getOrganizationName() {
        CentralRepoOrganization org = eamCase.getOrg();
        return org == null ? "" : org.getName();
    }

    /**
     * Get the case number of the CorrelationCase.
     *
     * @return the case number of the CorrelationCase.
     */
    String getCaseNumber() {
        return eamCase.getCaseNumber();
    }

    /**
     * Get the examiner name of the CorrelationCase.
     *
     * @return the examiner name of the CorrelationCase.
     */
    String getExaminerName() {
        return eamCase.getExaminerName();
    }

    /**
     * Get the examiner email of the CorrelationCase.
     *
     * @return the examiner email of the CorrelationCase.
     */
    String getExaminerEmail() {
        return eamCase.getExaminerEmail();
    }

    /**
     * Get the notes of the CorrelationCase.
     *
     * @return the notes of the CorrelationCase.
     */
    String getNotes() {
        return eamCase.getNotes();
    }

    /**
     * Get the examiner phone number of the CorrelationCase.
     *
     * @return the examiner phone number of the CorrelationCase.
     */
    String getExaminerPhone() {
        return eamCase.getExaminerPhone();
    }
}
