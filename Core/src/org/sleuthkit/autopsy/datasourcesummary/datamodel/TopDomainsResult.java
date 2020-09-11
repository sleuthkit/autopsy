/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Date;

/**
 * Describes a result of a program run on a datasource.
 */
public class TopDomainsResult {

    private final String domain;
    private final Long visitTimes;
    private final Date lastVisit;

    /**
     * Describes a top domain result.
     *
     * @param domain     The domain.
     * @param url        The url.
     * @param visitTimes The number of times it was visited.
     * @param lastVisit  The date of the last visit.
     */
    public TopDomainsResult(String domain, Long visitTimes, Date lastVisit) {
        this.domain = domain;
        this.visitTimes = visitTimes;
        this.lastVisit = lastVisit;
    }

    /**
     * @return The domain for the result.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * @return The number of times this site is visited.
     */
    public Long getVisitTimes() {
        return visitTimes;
    }

    /**
     * @return The date of the last visit.
     */
    public Date getLastVisit() {
        return lastVisit;
    }

}
