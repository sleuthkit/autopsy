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
    private final String url;
    private final Long visitTimes;
    private final Date lastVisit;

    public TopDomainsResult(String domain, String url, Long visitTimes, Date lastVisit) {
        this.domain = domain;
        this.url = url;
        this.visitTimes = visitTimes;
        this.lastVisit = lastVisit;
    }

    public String getDomain() {
        return domain;
    }

    public String getUrl() {
        return url;
    }

    public Long getVisitTimes() {
        return visitTimes;
    }

    public Date getLastVisit() {
        return lastVisit;
    }

    
}
