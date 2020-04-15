/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Stores the results from the various types of common attribute searching
 * Stores results based on how they are currently displayed in the UI
 */
final public class CommonAttributeCaseSearchResults {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeCaseSearchResults.class.getName());

    // maps instance count to list of attribute values. 
    private final Map<String, Map<String, CommonAttributeValueList>> caseNameToDataSources;

    /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param metadata            list of CommonAttributeValue indexed by case
     *                            name
     * @param percentageThreshold threshold to filter out files which are too
     *                            common, value of 0 is disabled
     * @param resultType          The type of Correlation Attribute being
     *                            searched for
     */
    CommonAttributeCaseSearchResults(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold, CorrelationAttributeInstance.Type resultType) {
        this.caseNameToDataSources = filterMetadata(metadata, percentageThreshold, resultType.getId());
    }

    /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param metadata            Map of Datasources and their
     *                            commonAttributeValueLists indexed by case name
     * @param percentageThreshold threshold to filter out files which are too
     *                            common, value of 0 is disabled
     */
    CommonAttributeCaseSearchResults(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold) {
        this.caseNameToDataSources = filterMetadata(metadata, percentageThreshold, CorrelationAttributeInstance.FILES_TYPE_ID);
    }

    /**
     * Find the child node whose children have the specified case name.
     *
     * This is a convenience method - you can also iterate over
     * <code>getValues()</code>.
     *
     * @param caseName caseNameKey
     *
     * @return list of values which represent matches
     */
    Map<String, CommonAttributeValueList> getAttributeValuesForCaseName(String caseName) {
        return this.caseNameToDataSources.get(caseName);
    }

    /**
     * Get an unmodifiable collection of values, indexed by case name, which
     * represents the common attributes found in the search.
     *
     * @return map of cases to data sources and their list of matches
     */
    public Map<String, Map<String, CommonAttributeValueList>> getMetadata() {
        return Collections.unmodifiableMap(this.caseNameToDataSources);
    }

    /**
     * Get an unmodifiable collection of values, indexed by case name, which
     * represents the common attributes found in the search.
     *
     * Remove results which are not found in the portion of available data
     * sources described by maximumPercentageThreshold.
     *
     * @param metadata            the unfiltered metadata
     * @param percentageThreshold the percentage threshold that a file should
     *                            not be more common than
     * @param resultTypeId        the ID of the result type contained in the
     *                            metadata
     *
     * @return metadata
     */
    private Map<String, Map<String, CommonAttributeValueList>> filterMetadata(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold, int resultTypeId) {
        try {
            final String currentCaseName;
            try {
                currentCaseName = Case.getCurrentCaseThrows().getDisplayName();
            } catch (NoCurrentCaseException ex) {
                throw new CentralRepoException("Unable to get current case while performing filtering", ex);
            }
            Map<String, CommonAttributeValueList> currentCaseDataSourceMap = metadata.get(currentCaseName);
            Map<String, Map<String, CommonAttributeValueList>> filteredCaseNameToDataSourcesTree = new HashMap<>();
            if (currentCaseDataSourceMap == null) { //there are no results
                return filteredCaseNameToDataSourcesTree;
            }
            CorrelationAttributeInstance.Type attributeType = CentralRepository.getInstance().getDefinedCorrelationTypes()
                    .stream()
                    .filter(filterType -> filterType.getId() == resultTypeId)
                    .findFirst().get();
            //Call countUniqueDataSources once to reduce the number of DB queries needed to get the frequencyPercentage
            Double uniqueCaseDataSourceTuples = CentralRepository.getInstance().getCountUniqueDataSources().doubleValue();
            Map<String, CommonAttributeValue> valuesToKeepCurrentCase = getValuesToKeepFromCurrentCase(currentCaseDataSourceMap, attributeType, percentageThreshold, uniqueCaseDataSourceTuples);
            for (Entry<String, Map<String, CommonAttributeValueList>> mapOfDataSources : Collections.unmodifiableMap(metadata).entrySet()) {
                if (!mapOfDataSources.getKey().equals(currentCaseName)) {
                    //rebuild the metadata structure with items from the current case substituted for their matches in other cases results we want to filter out removed
                    Map<String, CommonAttributeValueList> newTreeForCase = createTreeForCase(valuesToKeepCurrentCase, mapOfDataSources.getValue());
                    if (!newTreeForCase.isEmpty()) {
                        filteredCaseNameToDataSourcesTree.put(mapOfDataSources.getKey(), newTreeForCase);
                    }
                }
            }
            return filteredCaseNameToDataSourcesTree;
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.INFO, "Unable to perform filtering returning unfiltered result set", ex);
            return metadata;
        }

    }

    /**
     * Get the values from the results for the current case
     *
     * @param dataSourceToValueList      the map of datasources to their
     *                                   CommonAttributeValueLists for the
     *                                   current case
     * @param attributeType              the result type contained in the
     *                                   metadata
     * @param maximumPercentageThreshold the percentage threshold that a file
     *                                   should not be more common than
     * @param uniqueCaseDataSourceTuples the number of unique data sources in
     *                                   the CR
     *
     * @return a map of correlation value to CommonAttributeValue for results
     *         from the current case
     *
     * @throws CentralRepoException
     */
    private Map<String, CommonAttributeValue> getValuesToKeepFromCurrentCase(Map<String, CommonAttributeValueList> dataSourceToValueList, CorrelationAttributeInstance.Type attributeType, int maximumPercentageThreshold, Double uniqueCaseDataSourceTuples) throws CentralRepoException {
        Map<String, CommonAttributeValue> valuesToKeep = new HashMap<>();
        Set<String> valuesToRemove = new HashSet<>();
        for (Entry<String, CommonAttributeValueList> mapOfValueLists : Collections.unmodifiableMap(dataSourceToValueList).entrySet()) {
            for (CommonAttributeValue value : mapOfValueLists.getValue().getDelayedMetadataSet()) {
                if (valuesToRemove.contains(value.getValue())) {
                    //do nothing this value will not be added
                } else if (filterValue(attributeType, value, maximumPercentageThreshold, uniqueCaseDataSourceTuples)) {
                    valuesToRemove.add(value.getValue());
                } else {
                    valuesToKeep.put(value.getValue(), value);
                }
            }
        }
        return valuesToKeep;
    }

    /**
     * Create a new map representing the portion of the tree for a single case
     *
     * @param valuesToKeepCurrentCase a map of correlation value to
     *                                CommonAttributeValue for results from the
     *                                current case to substitute in
     * @param dataSourceToValueList   the reslts for a single case which need to
     *                                be filtered
     *
     * @return the modified results for the case
     *
     * @throws CentralRepoException
     */
    private Map<String, CommonAttributeValueList> createTreeForCase(Map<String, CommonAttributeValue> valuesToKeepCurrentCase, Map<String, CommonAttributeValueList> dataSourceToValueList) throws CentralRepoException {
        Map<String, CommonAttributeValueList> treeForCase = new HashMap<>();
        for (Entry<String, CommonAttributeValueList> mapOfValueLists : Collections.unmodifiableMap(dataSourceToValueList).entrySet()) {
            for (CommonAttributeValue value : mapOfValueLists.getValue().getDelayedMetadataSet()) {
                if (valuesToKeepCurrentCase.containsKey(value.getValue())) {
                    if (!treeForCase.containsKey(mapOfValueLists.getKey())) {
                        treeForCase.put(mapOfValueLists.getKey(), new CommonAttributeValueList());
                    }
                    treeForCase.get(mapOfValueLists.getKey()).addMetadataToList(valuesToKeepCurrentCase.get(value.getValue()));
                }
            }
        }
        return treeForCase;
    }

    /**
     * Determine if a value should be included in the results displayed to the
     * user
     *
     * @param attributeType              the result type contained in the
     *                                   metadata
     * @param value                      the correlationAttributeValue we are
     *                                   evaluating
     * @param maximumPercentageThreshold the percentage threshold that a file
     *                                   should not be more common than
     * @param uniqueCaseDataSourceTuples the number of unique data sources in
     *                                   the CR
     *
     * @return true if the value should be filtered and removed from what is
     *         shown to the user, false if the value should not be removed and
     *         the user will see it as a result
     *
     * @throws CentralRepoException
     */
    private boolean filterValue(CorrelationAttributeInstance.Type attributeType, CommonAttributeValue value, int maximumPercentageThreshold, Double uniqueCaseDataSourceTuples) throws CentralRepoException {
        if (maximumPercentageThreshold != 0) {  //only do the frequency filtering when a max % was set
            try {
                Double uniqueTypeValueTuples = CentralRepository.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(
                        attributeType, value.getValue()).doubleValue();
                Double commonalityPercentage = uniqueTypeValueTuples / uniqueCaseDataSourceTuples * 100;
                int frequencyPercentage = commonalityPercentage.intValue();
                if (frequencyPercentage > maximumPercentageThreshold) {
                    return true;
                }
            } catch (CorrelationAttributeNormalizationException ex) {
                LOGGER.log(Level.WARNING, "Unable to determine frequency percentage attribute - frequency filter may not be accurate for these results.", ex);
            }
        }
        return false;
    }
}
