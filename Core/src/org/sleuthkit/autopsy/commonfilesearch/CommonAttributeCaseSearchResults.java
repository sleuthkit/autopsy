/*
 *
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.commonfilesearch;

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
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

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
     * @param metadata            list of CommonAttributeValue indexed by size
     *                            of CommonAttributeValue
     * @param percentageThreshold threshold to filter out files which are too
     *                            common, value of 0 is disabled
     * @param resultType          The type of Correlation Attribute being
     *                            searched for
     * @param mimeTypesToFilterOn Set of mime types to include for intercase
     *                            searches
     */
    CommonAttributeCaseSearchResults(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold, CorrelationAttributeInstance.Type resultType, Set<String> mimeTypesToFilterOn) {
        this.caseNameToDataSources = filterMetadata(metadata, percentageThreshold, resultType.getId(), mimeTypesToFilterOn);
    }

    /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param metadata            list of CommonAttributeValue indexed by size
     *                            of CommonAttributeValue
     * @param percentageThreshold threshold to filter out files which are too
     *                            common, value of 0 is disabled
     */
    CommonAttributeCaseSearchResults(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold) {
        this.caseNameToDataSources = filterMetadata(metadata, percentageThreshold, CorrelationAttributeInstance.FILES_TYPE_ID, new HashSet<>());
    }

    /**
     * Find the child node whose children have the specified number of children.
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
     * Get an unmodifiable collection of values, indexed by number of
     * grandchildren, which represents the common attributes found in the
     * search.
     *
     * @return map of sizes of children to list of matches
     */
    public Map<String, Map<String, CommonAttributeValueList>> getMetadata() {
        return Collections.unmodifiableMap(this.caseNameToDataSources);
    }

    /**
     * Get an unmodifiable collection of values, indexed by number of
     * grandchildren, which represents the common attributes found in the
     * search.
     *
     * Remove results which are not found in the portion of available data
     * sources described by maximumPercentageThreshold.
     *
     * @return metadata
     */
    private Map<String, Map<String, CommonAttributeValueList>> filterMetadata(Map<String, Map<String, CommonAttributeValueList>> metadata, int percentageThreshold, int resultTypeId, Set<String> mimeTypesToFilterOn) {
        try {
            CorrelationAttributeInstance.Type attributeType = CorrelationAttributeInstance
                    .getDefaultCorrelationTypes()
                    .stream()
                    .filter(filterType -> filterType.getId() == resultTypeId)
                    .findFirst().get();
            final String currentCaseName;
            try {
                currentCaseName = Case.getCurrentCaseThrows().getDisplayName();
            } catch (NoCurrentCaseException ex) {
                throw new EamDbException("Unable to get current case while performing filtering", ex);
            }
            Double uniqueCaseDataSourceTuples = EamDb.getInstance().getCountUniqueDataSources().doubleValue();

            //Call countUniqueDataSources once to reduce the number of DB queries needed to get
            //the frequencyPercentage
            Map<String, CommonAttributeValueList> currentCaseDataSourceMap = metadata.get(currentCaseName);
            if (currentCaseDataSourceMap == null) {

                throw new EamDbException("No data for current case found in results, indicating there are no results and nothing will be filtered");
            }
            Map<String, Map<String, CommonAttributeValueList>> filteredCaseNameToDataSourcesTree = new HashMap<>();
            Map<String, CommonAttributeValue> valuesToKeepCurrentCase = getValuesToKeepFromCurrentCase(currentCaseDataSourceMap, attributeType, percentageThreshold, uniqueCaseDataSourceTuples, mimeTypesToFilterOn);
            for (Entry<String, Map<String, CommonAttributeValueList>> mapOfDataSources : Collections.unmodifiableMap(metadata).entrySet()) {
                if (!mapOfDataSources.getKey().equals(currentCaseName)) {
                    Map<String, CommonAttributeValueList> newTreeForCase = createTreeForCase(valuesToKeepCurrentCase, mapOfDataSources.getValue(), attributeType, percentageThreshold, uniqueCaseDataSourceTuples);
                    filteredCaseNameToDataSourcesTree.put(mapOfDataSources.getKey(), newTreeForCase);
                }
            }
            return filteredCaseNameToDataSourcesTree;
        } catch (EamDbException ex) {
            LOGGER.log(Level.INFO, "Unable to perform filtering returning unfiltered result set", ex);
            return metadata;
        }

    }

    private Map<String, CommonAttributeValue> getValuesToKeepFromCurrentCase(Map<String, CommonAttributeValueList> dataSourceToValueList, CorrelationAttributeInstance.Type attributeType, int maximumPercentageThreshold, Double uniqueCaseDataSourceTuples, Set<String> mimeTypesToFilterOn) throws EamDbException {
        Map<String, CommonAttributeValue> valuesToKeep = new HashMap<>();
        Set<String> valuesToRemove = new HashSet<>();
        for (Entry<String, CommonAttributeValueList> mapOfValueLists : Collections.unmodifiableMap(dataSourceToValueList).entrySet()) {
            for (CommonAttributeValue value : mapOfValueLists.getValue().getDelayedMetadataList()) {
                if (valuesToRemove.contains(value.getValue())) {
                    //do nothing this value will not be added
                } else if (filterValue(attributeType, value, maximumPercentageThreshold, uniqueCaseDataSourceTuples, mimeTypesToFilterOn)) {
                    valuesToRemove.add(value.getValue());
                } else {
                    valuesToKeep.put(value.getValue(), value);
                }
            }
        }
        return valuesToKeep;
    }

    private Map<String, CommonAttributeValueList> createTreeForCase(Map<String, CommonAttributeValue> valuesToKeepCurrentCase, Map<String, CommonAttributeValueList> dataSourceToValueList, CorrelationAttributeInstance.Type attributeType, int maximumPercentageThreshold, Double uniqueCaseDataSourceTuples) throws EamDbException {
        Map<String, CommonAttributeValueList> treeForCase = new HashMap<>();
        for (Entry<String, CommonAttributeValueList> mapOfValueLists : Collections.unmodifiableMap(dataSourceToValueList).entrySet()) {
            for (CommonAttributeValue value : mapOfValueLists.getValue().getDelayedMetadataList()) {
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

    private boolean filterValue(CorrelationAttributeInstance.Type attributeType, CommonAttributeValue value, int maximumPercentageThreshold, Double uniqueCaseDataSourceTuples, Set<String> mimeTypesToInclude) throws EamDbException {
        //Intracase common attribute searches will have been created with an empty mimeTypesToInclude list 
        //because when performing intra case search this filtering will have been done during the query of the case database 
        boolean mimeTypeToRemove = false;  //allow code to be more efficient by not attempting to remove the same value multiple times
        if (!mimeTypesToInclude.isEmpty()) { //only do the mime type filtering when mime types aren't empty
            for (AbstractCommonAttributeInstance commonAttr : value.getInstances()) {
                AbstractFile abstractFile = commonAttr.getAbstractFile();
                if (abstractFile != null) {
                    String mimeType = abstractFile.getMIMEType();
                    if (mimeType != null && !mimeTypesToInclude.contains(mimeType)) {
                        return true;
                    }
                }
            }
        }
        if (!mimeTypeToRemove && maximumPercentageThreshold != 0) {  //only do the frequency filtering when a max % was set
            try {
                Double uniqueTypeValueTuples = EamDb.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(
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

    /**
     * How many distinct common files exist for this search results?
     *
     * @return number of common files
     */
    public int size() {
        int count = 0;
        for (Entry<String, Map<String, CommonAttributeValueList>> dataSourceToValueList : Collections.unmodifiableMap(this.caseNameToDataSources).entrySet()) {
            for (Entry<String, CommonAttributeValueList> mapOfValueLists : Collections.unmodifiableMap(dataSourceToValueList.getValue()).entrySet()) {
                count += mapOfValueLists.getValue().getDelayedMetadataList().size();
            }
        }
        return count;
    }
}
