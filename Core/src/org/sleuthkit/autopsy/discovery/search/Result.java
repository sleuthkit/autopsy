/*
 * Autopsy
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Interface implemented by all types of results.
 */
public abstract class Result {

    private SearchData.Frequency frequency = SearchData.Frequency.UNKNOWN;
    private SearchData.PreviouslyNotable notabilityStatus = SearchData.PreviouslyNotable.NOT_PREVIOUSLY_NOTABLE;
    private final List<String> tagNames = new ArrayList<>();

    /**
     * Get the Object ID for the data source the result is in.
     *
     * @return The Object ID of the data source the result is in.
     */
    public abstract long getDataSourceObjectId();

    /**
     * Get the frequency of this result in the central repository.
     *
     * @return The Frequency enum.
     */
    public SearchData.Frequency getFrequency() {
        return frequency;
    }

    /**
     * Get the known status of the result (known status being NSRL).
     *
     * @return The Known status of the result.
     */
    public abstract TskData.FileKnown getKnown();
    
    /**
     * Mark the result as being previously notable in the CR.
     */
    final public void markAsPreviouslyNotableInCR() {
        this.notabilityStatus = SearchData.PreviouslyNotable.PREVIOUSLY_NOTABLE;
    }
    
    /**
     * Get the previously notable value of this result.
     * 
     * @return The previously notable status enum.
     */
    final public SearchData.PreviouslyNotable getPreviouslyNotableInCR() {
        return this.notabilityStatus;
    }

    /**
     * Set the frequency of this result in the central repository.
     *
     * @param frequency The frequency of the result as an enum.
     */
    final public void setFrequency(SearchData.Frequency frequency) {
        this.frequency = frequency;
    }

    /**
     * Get the data source associated with this result.
     *
     * @return The data source this result came from.
     *
     * @throws TskCoreException
     */
    public abstract Content getDataSource() throws TskCoreException;

    /**
     * Get the type of this result.
     *
     * @return The type of items being searched for.
     */
    public abstract SearchData.Type getType();

    /**
     * Add a tag name that matched this file.
     *
     * @param tagName
     */
    public void addTagName(String tagName) {
        if (!tagNames.contains(tagName)) {
            tagNames.add(tagName);
        }

        // Sort the list so the getTagNames() will be consistent regardless of the order added
        Collections.sort(tagNames);
    }

    /**
     * Get the tag names for this file
     *
     * @return the tag names that matched this file.
     */
    public List<String> getTagNames() {
        return Collections.unmodifiableList(tagNames);
    }
}
