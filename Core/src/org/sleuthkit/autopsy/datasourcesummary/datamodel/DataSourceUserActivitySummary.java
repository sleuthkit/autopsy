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
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_DEVICE_ATTACHED;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import static org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities.SortOrder;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_ID;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MAKE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DEVICE_MODEL;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MAC_ADDRESS;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT;

/**
 * Provides summary information about top domains in a datasource. At this time,
 * the data being provided is fictitious and is done as a placeholder.
 */
public class DataSourceUserActivitySummary {

    private static final BlackboardArtifact.Type TYPE_DEVICE_ATTACHED = new BlackboardArtifact.Type(TSK_DEVICE_ATTACHED);
    private static final BlackboardArtifact.Type TYPE_MESSAGE = new BlackboardArtifact.Type(TSK_MESSAGE);
    private static final BlackboardArtifact.Type TYPE_WEB_SEARCH_QUERY = new BlackboardArtifact.Type(TSK_WEB_SEARCH_QUERY);
    
    private static final BlackboardAttribute.Type TYPE_DATETIME = new BlackboardAttribute.Type(TSK_DATETIME);
    private static final BlackboardAttribute.Type TYPE_DATETIME_ACCESSED = new BlackboardAttribute.Type(TSK_DATETIME_ACCESSED);
    private static final BlackboardAttribute.Type TYPE_DEVICE_ID = new BlackboardAttribute.Type(TSK_DEVICE_ID);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MAKE = new BlackboardAttribute.Type(TSK_DEVICE_MAKE);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MODEL = new BlackboardAttribute.Type(TSK_DEVICE_MODEL);
    private static final BlackboardAttribute.Type TYPE_MAC_ADDRESS = new BlackboardAttribute.Type(TSK_MAC_ADDRESS);
    private static final BlackboardAttribute.Type TYPE_MESSAGE_TYPE = new BlackboardAttribute.Type(TSK_MESSAGE_TYPE);
    private static final BlackboardAttribute.Type TYPE_TEXT = new BlackboardAttribute.Type(TSK_TEXT);
    private static final BlackboardAttribute.Type TYPE_DOMAIN = new BlackboardAttribute.Type(TSK_DOMAIN);
    private static final BlackboardAttribute.Type TYPE_PROG_NAME = new BlackboardAttribute.Type(TSK_PROG_NAME);

    private static final long SLEEP_TIME = 5000;

    /**
     * A function to calculate a result from 2 parameters.
     */
    interface Function2<A1, A2, O> {

        O apply(A1 a1, A2 a2);
    }

    /**
     * Gets a list of recent domains based on the datasource.
     *
     * @param dataSource The datasource to query for recent domains.
     * @param count      The max count of items to return.
     *
     * @return The list of items retrieved from the database.
     *
     * @throws InterruptedException
     */
    public List<TopDomainsResult> getRecentDomains(DataSource dataSource, int count) throws InterruptedException {
        Thread.sleep(SLEEP_TIME);
        final String dId = Long.toString(dataSource.getId());
        final Function2<String, Integer, String> getId = (s, idx) -> String.format("d:%s, f:%s, i:%d", dId, s, idx);
        return IntStream.range(0, count)
                .mapToObj(num -> new TopDomainsResult(
                getId.apply("domain", num),
                getId.apply("url", num),
                (long) num,
                new Date(((long) num) * 1000 * 60 * 60 * 24)
        ))
                .collect(Collectors.toList());
    }

    private final SleuthkitCaseProvider caseProvider;
    private final TextTranslationService translationService;
    private final java.util.logging.Logger logger;

    public DataSourceUserActivitySummary() {
        this(SleuthkitCaseProvider.DEFAULT, TextTranslationService.getInstance(), 
                org.sleuthkit.autopsy.coreutils.Logger.getLogger(DataSourceUserActivitySummary.class.getName()));
    }

    public DataSourceUserActivitySummary(SleuthkitCaseProvider provider, TextTranslationService translationService, java.util.logging.Logger logger) {
        this.caseProvider = provider;
        this.translationService = translationService;
        this.logger = logger;
    }

    public List<TopWebSearchResult> getMostRecentWebSearches(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        List<TopWebSearchResult> results =
                DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_WEB_SEARCH_QUERY, dataSource, TYPE_DATETIME_ACCESSED, SortOrder.DESCENDING, count)
                .stream()
                .map(artifact -> new TopWebSearchResult(
                        DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_TEXT),
                        DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME_ACCESSED),
                        DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DOMAIN),
                        DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_PROG_NAME)
                ))
                .collect(Collectors.toList());
        
        for (TopWebSearchResult result : results) {
            if (StringUtils.isNotBlank(result.getSearchString()) && translationService.hasProvider()) {
                String translated = null;
                try {
                    translated = translationService.translate(result.getSearchString());
                } catch (NoServiceProviderException | TranslationException ex) {
                    logger.log(Level.WARNING, String.format("There was an error translating text: '%s'", result.getSearchString()), ex);
                }
                
                if (StringUtils.isNotBlank(translated)) {
                    result.setTranslatedResult(translated);    
                }
            }
        }
        
        return results;
    }

    public List<TopDeviceAttachedResult> getRecentDevices(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        return DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_DEVICE_ATTACHED, dataSource, TYPE_DATETIME, SortOrder.DESCENDING, count)
                .stream()
                .map(artifact -> new TopDeviceAttachedResult(
                DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_ID),
                DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME),
                DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MAKE),
                DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MODEL),
                DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_MAC_ADDRESS)
        ))
                .collect(Collectors.toList());
    }

    public List<TopAccountResult> getRecentAccounts(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        // TODO fix this for groupings
        return DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_MESSAGE, dataSource, TYPE_DATETIME, SortOrder.DESCENDING, count)
                .stream()
                .map(artifact -> new TopAccountResult(
                DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_MESSAGE_TYPE),
                DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME)
        ))
                .collect(Collectors.toList());
    }

    public static class TopWebSearchResult {

        private final String searchString;
        private final Date dateAccessed;
        private final String domain;
        private final String programName;
        private String translatedResult;

        public TopWebSearchResult(String searchString, Date dateAccessed, String domain, String programName) {
            this.searchString = searchString;
            this.dateAccessed = dateAccessed;
            this.domain = domain;
            this.programName = programName;
        }

        public String getTranslatedResult() {
            return translatedResult;
        }

        public void setTranslatedResult(String translatedResult) {
            this.translatedResult = translatedResult;
        }

        public String getSearchString() {
            return searchString;
        }

        public Date getDateAccessed() {
            return dateAccessed;
        }

        public String getDomain() {
            return domain;
        }

        public String getProgramName() {
            return programName;
        }
    }

    public static class TopDeviceAttachedResult {

        private final String deviceId;
        private final Date dateAccessed;
        private final String deviceMake;
        private final String deviceModel;
        private final String macAddress;

        public TopDeviceAttachedResult(String deviceId, Date dateAccessed, String deviceMake, String deviceModel, String macAddress) {
            this.deviceId = deviceId;
            this.dateAccessed = dateAccessed;
            this.deviceMake = deviceMake;
            this.deviceModel = deviceModel;
            this.macAddress = macAddress;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public Date getDateAccessed() {
            return dateAccessed;
        }

        public String getDeviceMake() {
            return deviceMake;
        }

        public String getDeviceModel() {
            return deviceModel;
        }

        public String getMacAddress() {
            return macAddress;
        }
    }

    public static class TopAccountResult {

        private final String accountType;
        private final Date lastAccess;

        public TopAccountResult(String accountType, Date lastAccess) {
            this.accountType = accountType;
            this.lastAccess = lastAccess;
        }

        public String getAccountType() {
            return accountType;
        }

        public Date getLastAccess() {
            return lastAccess;
        }
    }
}
