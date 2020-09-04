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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Providing data for the data source analysis tab.
 */
public class DataSourceAnalysisSummary {
    private static final BlackboardAttribute.Type TYPE_SET_NAME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME);
    
    private final java.util.logging.Logger logger;
    private final SleuthkitCaseProvider provider;

    public DataSourceAnalysisSummary() {
        this(Logger.getLogger(DataSourceAnalysisSummary.class.getName()), SleuthkitCaseProvider.DEFAULT);
    }
    
    public DataSourceAnalysisSummary(java.util.logging.Logger logger, SleuthkitCaseProvider provider) {
        this.logger = logger;
        this.provider = provider;
    }
    
    public List<Pair<String, Long>> getHashsetCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT);
    }

    public List<Pair<String, Long>> getKeywordCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT);
    }
    
    public List<Pair<String, Long>> getInterestingItemCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }
    
    private List<Pair<String, Long>> getCountsData(DataSource dataSource, BlackboardAttribute.Type keyType, ARTIFACT_TYPE... artifactTypes) throws SleuthkitCaseProviderException, TskCoreException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        SleuthkitCase skCase = provider.get();
        
        for (ARTIFACT_TYPE type : artifactTypes) {
            artifacts.addAll(skCase.getBlackboard().getArtifacts(type.getTypeID(), dataSource.getId()));
        }
        
        Map<String, Long> countedKeys = artifacts.stream()
                .map((art) -> {
                    String key = DataSourceInfoUtilities.getStringOrNull(art, keyType);
                    return (StringUtils.isBlank(key)) ? null : key;
                })
                .filter((key) -> key != null)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        
        return countedKeys.entrySet().stream()
                .map((e) -> Pair.of(e.getKey(), e.getValue()))
                .sorted((a,b) -> -a.getValue().compareTo(b.getValue()))
                .collect(Collectors.toList());
    }
}
