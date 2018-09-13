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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Stores the results from the various types of common attribute searching
 * Stores results based on how they are currently displayed in the UI
 */
final public class CommonAttributeSearchResults {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeSearchResults.class.getName());
    
    // maps instance count to list of attribute values. 
    private final Map<Integer, CommonAttributeValueList> instanceCountToAttributeValues;

    private final int percentageThreshold;
    private final int resultTypeId;
    
    /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param values list of CommonAttributeValue indexed by size of
     * CommonAttributeValue
     */
    CommonAttributeSearchResults(Map<Integer, CommonAttributeValueList> metadata, int percentageThreshold, CorrelationAttributeInstance.Type resultType) {
        //wrap in a new object in case any client code has used an unmodifiable collection
        this.instanceCountToAttributeValues = new HashMap<>(metadata);
        this.percentageThreshold = percentageThreshold;
        this.resultTypeId = resultType.getId();
    }
    
        /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param values list of CommonAttributeValue indexed by size of
     * CommonAttributeValue
     */
    CommonAttributeSearchResults(Map<Integer, CommonAttributeValueList> metadata, int percentageThreshold) {
        //wrap in a new object in case any client code has used an unmodifiable collection
        this.instanceCountToAttributeValues = new HashMap<>(metadata);
        this.percentageThreshold = percentageThreshold;
        this.resultTypeId = CorrelationAttributeInstance.FILES_TYPE_ID;
    }

    /**
     * Find the child node whose children have the specified number of children.
     *
     * This is a convenience method - you can also iterate over
     * <code>getValues()</code>.
     *
     * @param instanceCount key
     * @return list of values which represent matches
     */
    CommonAttributeValueList getAttributeValuesForInstanceCount(Integer instanceCount) {
        return this.instanceCountToAttributeValues.get(instanceCount);
    }

    /**
     * Get an unmodifiable collection of values, indexed by number of
     * grandchildren, which represents the common attributes found in the
     * search.
     *
     * @return map of sizes of children to list of matches
     */
    public Map<Integer, CommonAttributeValueList> getMetadata() throws EamDbException {
        if(this.percentageThreshold == 0){
            return Collections.unmodifiableMap(this.instanceCountToAttributeValues);
        } else {
            return this.getMetadata(this.percentageThreshold);
        }
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
    private Map<Integer, CommonAttributeValueList> getMetadata(int maximumPercentageThreshold) throws EamDbException {
        
        if(maximumPercentageThreshold == 0){
            return Collections.unmodifiableMap(this.instanceCountToAttributeValues);
        }
        
        CorrelationAttributeInstance.Type fileAttributeType = CorrelationAttributeInstance
                .getDefaultCorrelationTypes()
                .stream()
                .filter(filterType -> filterType.getId() == this.resultTypeId)
                .findFirst().get();
        
        EamDb eamDb = EamDb.getInstance();
        
        Map<Integer, List<CommonAttributeValue>> itemsToRemove = new HashMap<>();
        Double uniqueCaseDataSourceTuples = eamDb.getCountUniqueDataSources().doubleValue();
        
        for(Entry<Integer, CommonAttributeValueList> listOfValues : Collections.unmodifiableMap(this.instanceCountToAttributeValues).entrySet()){
            
            final Integer key = listOfValues.getKey();
            final CommonAttributeValueList values = listOfValues.getValue();
            
            for(CommonAttributeValue value : values.getDelayedMetadataList()){ // Need the real metadata
                
                try {
                    CorrelationAttributeInstance corAttr = new CorrelationAttributeInstance(fileAttributeType, value.getValue());
                    Double uniqueTypeValueTuples = eamDb.getCountUniqueCaseDataSourceTuplesHavingTypeValue(
                            corAttr.getCorrelationType(), corAttr.getCorrelationValue()).doubleValue();
                    Double commonalityPercentage = uniqueTypeValueTuples / uniqueCaseDataSourceTuples * 100;
                    int frequencyPercentage = commonalityPercentage.intValue();
                
                    if(frequencyPercentage > maximumPercentageThreshold){
                        if(itemsToRemove.containsKey(key)){
                            itemsToRemove.get(key).add(value);
                        } else {
                            List<CommonAttributeValue> toRemove = new ArrayList<>();
                            toRemove.add(value);
                            itemsToRemove.put(key, toRemove);
                        }
                    }
                } catch(CorrelationAttributeNormalizationException ex){
                    LOGGER.log(Level.WARNING, "Unable to determine frequency percentage attribute - frequency filter may not be accurate for these results.", ex);
                }
            }
        }
        
        for(Entry<Integer, List<CommonAttributeValue>> valuesToRemove : itemsToRemove.entrySet()){
            
            final Integer key = valuesToRemove.getKey();
            final List<CommonAttributeValue> values = valuesToRemove.getValue();
            
            for (CommonAttributeValue value : values){
                final CommonAttributeValueList instanceCountValue = this.instanceCountToAttributeValues.get(key);
                instanceCountValue.removeMetaData(value);
                
                if(instanceCountValue.getDelayedMetadataList().isEmpty()){ // Check the real metadata
                    this.instanceCountToAttributeValues.remove(key);
                }
            }
        }
        
        return Collections.unmodifiableMap(this.instanceCountToAttributeValues);
    }

    /**
     * How many distinct common files exist for this search results?
     *
     * @return number of common files
     */
    public int size() {

        int count = 0;
        for (CommonAttributeValueList data : this.instanceCountToAttributeValues.values()) {
            for(CommonAttributeValue md5 : data.getDelayedMetadataList()){
                count += md5.getInstanceCount();
            }
        }
        return count;
    }
}