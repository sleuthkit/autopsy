/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation.datamodel;

import java.util.ArrayList;
import java.util.List;
import org.python.google.common.collect.ImmutableList;

/**
 * The result of attempting to parse GeoLocation objects.
 */
public class GeoLocationParseResult<T> {

    private boolean successfullyParsed;
    private final List<T> items = new ArrayList<>();

    /**
     * Returns a GeoLocationParseResult with no items and declared successfully
     * parsed.
     */
    public GeoLocationParseResult() {
        successfullyParsed = true;
    }

    /**
     * Returns a new GeoLocationParseResult.
     *
     * @param items              The items to copy to this result (can be null).
     * @param successfullyParsed Whether or not the operation was entirely
     *                           successful.
     */
    public GeoLocationParseResult(List<T> items, boolean successfullyParsed) {
        this.successfullyParsed = successfullyParsed;

        if (items != null) {
            this.items.addAll(items);
        }
    }

    /**
     * Adds the content of the GeoLocationParseResult parameter to this. Items
     * will be concatenated and this object's successfullyParsed status will be
     * true if it is already true and the object is true as well.
     *
     * @param toAdd The GeoLocationParseResult to add.
     */
    public void add(GeoLocationParseResult<T> toAdd) {
        this.successfullyParsed = this.successfullyParsed && toAdd.isSuccessfullyParsed();
        this.items.addAll(toAdd.getItems());
    }

    /**
     * Whether or not the GeoLocation object has been successfully parsed.
     *
     * @return Whether or not the GeoLocation object has been successfully
     *         parsed.
     */
    public boolean isSuccessfullyParsed() {
        return successfullyParsed;
    }

    /**
     * @return The successfully parsed GeoLocation objects.
     */
    public List<T> getItems() {
        return ImmutableList.copyOf(items);
    }
}
