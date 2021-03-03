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
package org.sleuthkit.autopsy.discovery.search;

import org.sleuthkit.autopsy.testutils.TskMockUtils;
import org.sleuthkit.datamodel.Content;

/**
 * Mock utility methods for DomainSearchTests
 */
public class DomainSearchTestUtils {
    
    private DomainSearchTestUtils() {
        
    }
        
    public static ResultDomain mockDomainResult(String domain, long start, long end, 
            long totalVisits, long visits, long filesDownloaded, long dataSourceId) {
        Content dataSource = TskMockUtils.getDataSource(dataSourceId);
        return new ResultDomain(domain, start, end, totalVisits,
                visits, filesDownloaded, 0L, "", dataSource);
    }
    
    public static ResultDomain mockDomainResult(String domain) {
        return DomainSearchTestUtils.mockDomainResult(domain, 0, 0, 0, 0, 0, 0);
    }   
}
