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
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;

/**
 * Stores the results from the various types of common attribute searching
 * Stores results based on how they are currently displayed in the UI
 */
final public class CommonAttributeSearchResults {

    // maps instance count to list of attribute values. 
    private final Map<Integer, List<CommonAttributeValue>> instanceCountToAttributeValues;

    private final int percentageThreshold;
    
    /**
     * Create a values object which can be handed off to the node factories.
     *
     * @param values list of CommonAttributeValue indexed by size of
     * CommonAttributeValue
     */
    CommonAttributeSearchResults(Map<Integer, List<CommonAttributeValue>> metadata, int percentageThreshold) {
        //wrap in a new object in case any client code has used an unmodifiable collection
        this.instanceCountToAttributeValues = new HashMap<>(metadata);
        this.percentageThreshold = percentageThreshold;
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
    List<CommonAttributeValue> getAttributeValuesForInstanceCount(Integer instanceCount) {
        return this.instanceCountToAttributeValues.get(instanceCount);
    }

    /**
     * Get an unmodifiable collection of values, indexed by number of
     * grandchildren, which represents the common attributes found in the
     * search.
     *
     * @return map of sizes of children to list of matches
     */
    public Map<Integer, List<CommonAttributeValue>> getMetadata() throws EamDbException {
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
     * sources described by minimumPercentageThreshold.
     * 
     * @return metadata
     */
    private Map<Integer, List<CommonAttributeValue>> getMetadata(int minimumPercentageThreshold) throws EamDbException {
        
        if(minimumPercentageThreshold == 0){
            return Collections.unmodifiableMap(this.instanceCountToAttributeValues);
        }
        
        CorrelationAttributeInstance.Type fileAttributeType = CorrelationAttributeInstance
                .getDefaultCorrelationTypes()
                .stream()
                .filter(filterType -> filterType.getId() == CorrelationAttributeInstance.FILES_TYPE_ID)
                .findFirst().get();
        
        EamDb eamDb = EamDb.getInstance();
        
        Map<Integer, List<CommonAttributeValue>> itemsToRemove = new HashMap<>();
        
        for(Entry<Integer, List<CommonAttributeValue>> listOfValues : Collections.unmodifiableMap(this.instanceCountToAttributeValues).entrySet()){
            
            final Integer key = listOfValues.getKey();
            final List<CommonAttributeValue> values = listOfValues.getValue();
            
            for(CommonAttributeValue value : values){
                
                int frequencyPercentage = eamDb.getFrequencyPercentage(new CorrelationAttributeInstance(fileAttributeType, value.getValue()));
                
                if(frequencyPercentage < minimumPercentageThreshold){
                    if(itemsToRemove.containsKey(key)){
                        itemsToRemove.get(key).add(value);
                    } else {
                        List<CommonAttributeValue> toRemove = new ArrayList<>();
                        toRemove.add(value);
                        itemsToRemove.put(key, toRemove);
                    }
                }
            }
        }
        
        for(Entry<Integer, List<CommonAttributeValue>> valuesToRemove : itemsToRemove.entrySet()){
            
            final Integer key = valuesToRemove.getKey();
            final List<CommonAttributeValue> values = valuesToRemove.getValue();
            
            for (CommonAttributeValue value : values){
                final List<CommonAttributeValue> instanceCountValue = this.instanceCountToAttributeValues.get(key);
                instanceCountValue.remove(value);
                
                if(instanceCountValue.isEmpty()){
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
        for (List<CommonAttributeValue> data : this.instanceCountToAttributeValues.values()) {
            for (CommonAttributeValue md5 : data) {
                count += md5.getInstanceCount();
            }
        }
        return count;
    }
}
