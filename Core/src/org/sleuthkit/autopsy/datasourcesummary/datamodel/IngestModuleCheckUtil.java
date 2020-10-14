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

import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for checking if an ingest module has been run on a datasource.
 */
@Messages({
    "IngestModuleCheckUtil_recentActivityModuleName=Recent Activity",
    
})
public class IngestModuleCheckUtil {
    public static final String RECENT_ACTIVITY_FACTORY = "org.sleuthkit.autopsy.recentactivity.RecentActivityExtracterModuleFactory";
    public static final String RECENT_ACTIVITY_MODULE_NAME = Bundle.IngestModuleCheckUtil_recentActivityModuleName();
    
    // IngestModuleInfo separator for unique_name
    private static final String UNIQUE_NAME_SEPARATOR = "-";

    private final SleuthkitCaseProvider caseProvider;
        
    /**
     * Main constructor.
     */
    public IngestModuleCheckUtil() {
        this(SleuthkitCaseProvider.DEFAULT);

    }

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider The object providing the current SleuthkitCase.
     */
    public IngestModuleCheckUtil(SleuthkitCaseProvider provider) {

        this.caseProvider = provider;
    }

    
    /**
     * Gets the fully qualified factory from the IngestModuleInfo.
     * @param info The IngestJobInfo.
     * @return The fully qualified factory.
     */
    private static String getFullyQualifiedFactory(IngestModuleInfo info) {
        if (info == null) {
            return null;
        }
        
        String qualifiedName = info.getUniqueName();
        if (StringUtils.isBlank(qualifiedName)) {
            return null;
        }
        
        return qualifiedName.split(UNIQUE_NAME_SEPARATOR)[0];
    }
        

    /**
     * Whether or not the ingest job info contains the ingest modulename.
     * @param info The IngestJobInfo.
     * @param fullyQualifiedFactory The fully qualified classname of the relevant factory.
     * @return True if the ingest module name is contained in the data.
     */
    private static boolean hasIngestModule(IngestJobInfo info, String fullyQualifiedFactory) {
        if (info == null || info.getIngestModuleInfo() == null || StringUtils.isBlank(fullyQualifiedFactory)) {
            return false;
        }

        return info.getIngestModuleInfo().stream()
                .anyMatch((moduleInfo) -> {
                    String thisQualifiedFactory = getFullyQualifiedFactory(moduleInfo);
                    return fullyQualifiedFactory.equalsIgnoreCase(thisQualifiedFactory);
                });
    }

    /**
     * Whether or not a data source has been ingested with a particular ingest module.
     * @param dataSource The datasource.
     * @param fullyQualifiedFactory The fully qualified classname of the relevant factory.
     * @return Whether or not a data source has been ingested with a particular ingest module.
     * @throws TskCoreException 
     * @throws SleuthkitCaseProviderException
     */
    public boolean isModuleIngested(DataSource dataSource, String fullyQualifiedFactory)
            throws TskCoreException, SleuthkitCaseProviderException {
        if (dataSource == null) {
            return false;
        }

        long dataSourceId = dataSource.getId();

        return caseProvider.get().getIngestJobs().stream()
                .anyMatch((ingestJob) -> {
                    return ingestJob != null
                            && ingestJob.getObjectId() == dataSourceId
                            && hasIngestModule(ingestJob, fullyQualifiedFactory);
                });

    }
    
    /**
     * Get a mapping of fully qualified factory name to display name.
     * @param skCase The SleuthkitCase.
     * @return The mapping of fully qualified factory name to display name.
     * @throws TskCoreException 
     */
    public static Map<String, String> getFactoryDisplayNames(SleuthkitCase skCase) throws TskCoreException {
        return skCase.getIngestJobs().stream()
                .flatMap(ingestJob -> ingestJob.getIngestModuleInfo().stream())
                .collect(Collectors.toMap(
                        (moduleInfo) -> getFullyQualifiedFactory(moduleInfo), 
                        (moduleInfo) -> moduleInfo.getDisplayName(),
                        (a,b) -> a));
    }
}
