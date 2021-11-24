/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.List;

/**
 * Interface for all search results that are used to display in the table/DataResultViewer area. 
 */
public interface SearchResultsDTO {

    // returns the type of data
    String getTypeId();
    
    // Returns a unique signature for the type of data.  Used keep track of custom column ordering. 
    String getSignature();

    // Text to display at top of the table about the type of the results. 
    String getDisplayName();

    // Sorted list of column headers. The RowDTO column values will be in the same order
    List<ColumnKey> getColumns();

    // Page-sized, sorted list of rows to display
    List<RowDTO> getItems();

    // total number of results (could be bigger than what is in the results)
    long getTotalResultsCount();
    
    // Index in the total results that this set/page starts at
    long getStartItem();
}
