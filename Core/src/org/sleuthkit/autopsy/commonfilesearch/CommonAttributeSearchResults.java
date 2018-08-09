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
import java.util.List;
import java.util.Map;

/**
 * Stores the results from the various types of common attribute searching
 * Stores results based on how they are currently displayed in the UI
 */
final public class CommonAttributeSearchResults {
    
    // maps instance count to list of attribute values. 
    private final Map<Integer, List<CommonAttributeValue>> instanceCountToAttributeValues;
    
    /**
     * Create a values object which can be handed off to the node factories.
     * 
     * @param values list of CommonAttributeValue indexed by size of 
     * CommonAttributeValue
     */
    CommonAttributeSearchResults(Map<Integer, List<CommonAttributeValue>> metadata){
        this.instanceCountToAttributeValues = metadata;
    }

    /**
     * Find the child node whose children have the specified number of children.
     *
     * This is a convenience method - you can also iterate over
     * <code>getValues()</code>.
     *
     * @param isntanceCound key
     * @return list of values which represent matches
     */
    List<CommonAttributeValue> getAttributeValuesForInstanceCount(Integer instanceCount) {
        return this.instanceCountToAttributeValues.get(instanceCount);
    }

 /**
     * Get an unmodifiable collection of values, indexed by number of 
     * grandchildren, which represents the common attributes found in the 
     * search.
     * @return map of sizes of children to list of matches
     */    
public Map<Integer, List<CommonAttributeValue>> getMetadata() {
        return Collections.unmodifiableMap(this.instanceCountToAttributeValues);
    }

    /**
     * How many distinct common files exist for this search results?
     * @return number of common files
     */
    public int size() {
                
        int count = 0;
        for (List<CommonAttributeValue> data : this.instanceCountToAttributeValues.values()) {
            for(CommonAttributeValue md5 : data){
                count += md5.getInstanceCount();
            }
        }
        return count;
    }
}
