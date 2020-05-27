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
import org.apache.commons.collections4.ListUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * The result of attempting to parse a GeoLocation object.
 */
public class GeoLocationParseResult<T> {

    private final boolean successfullyParsed;
    private final GeoLocationDataException exception;
    private final BlackboardArtifact artifact;
    private final T geoObject;

    GeoLocationParseResult(BlackboardArtifact artifact,
            boolean successfullyParsed, GeoLocationDataException exception, T geoObject) {

        this.successfullyParsed = successfullyParsed;
        this.exception = exception;
        this.artifact = artifact;
        this.geoObject = geoObject;
    }

    /**
     * Creates a GeoLocationParseResult as the result of a successful parse.
     * @param <T>           The type of object to parse.
     * @param artifact      The artifact that was parsed or failed in parsing
     *                      (could be null).
     * @param geoObject     The GeoLocation object to include in result.
     *
     * @return The generated WaypointParseResult.
     */
    public static <T> GeoLocationParseResult<T> create(BlackboardArtifact artifact, T geoObject) {
        if (geoObject == null) {
            return new GeoLocationParseResult(artifact, false, new GeoLocationDataException("GeoLocation object provided was null"), null);
        }

        return new GeoLocationParseResult(artifact, true, null, geoObject);
    }

    /**
     * Creates a GeoLocationParseResult indicating a failed parsing.
     *
     * @param <T>           The type of GeoLocation object that was supposed to
     *                      be parsed.
     * @param artifact      The artifact that was parsed or failed in parsing
     *                      (could be null).
     * @param exception     The exception generated.
     *
     * @return The GeoLocationParseResult indicating an error.
     */
    public static <T> GeoLocationParseResult<T> error(BlackboardArtifact artifact, GeoLocationDataException exception) {
        return new GeoLocationParseResult(artifact, false, exception, null);
    }

    /**
     * The result of splitting an iterable of GeoLocationParseResults into a
     * list of successfully parsed items and a list of failures.
     *
     * @param <T> The parsed item type.
     */
    public static class SeparationResult<T> {

        private final List<GeoLocationParseResult<T>> failedItems;
        private final List<T> parsedItems;

        SeparationResult(List<GeoLocationParseResult<T>> failedItems, List<T> parsedItems) {
            this.failedItems = failedItems;
            this.parsedItems = parsedItems;
        }

        /**
         * @return The items that failed to parse properly.
         */
        public List<GeoLocationParseResult<T>> getFailedItems() {
            return failedItems;
        }

        /**
         * @return The underlying items that were successfully parsed.
         */
        public List<T> getParsedItems() {
            return parsedItems;
        }
    }

    /**
     * Separates an iterable of GeoLocationParseResult objects into failed items
     * and successfully parsed items.
     *
     * @param <T>   The underlying type that was parsed.
     * @param items The items to separate.
     *
     * @return The SeparationResult with successfully parsed and failed items.
     */
    public static <T> SeparationResult<T> separate(Iterable<? extends GeoLocationParseResult<T>> items) {
        List<GeoLocationParseResult<T>> failedItems = new ArrayList<>();
        List<T> parsedItems = new ArrayList<>();
        for (GeoLocationParseResult<T> item : items) {
            if (item.isSuccessfullyParsed()) {
                parsedItems.add(item.getGeoLocationObject());
            } else {
                failedItems.add(item);
            }
        }

        return new SeparationResult<T>(ListUtils.unmodifiableList(failedItems), ListUtils.unmodifiableList(parsedItems));
    }

    /**
     * Whether or not the GeoLocation object has been successfully parsed. If
     * true, there should be a non-null GeoLocation object present. Otherwise,
     * there should be a non-null exception.
     *
     * @return Whether or not the GeoLocation object has been successfully
     *         parsed.
     */
    public boolean isSuccessfullyParsed() {
        return successfullyParsed;
    }

    /**
     * @return The exception caused in attempting to parse the GeoLocation
     *         object if there was an exception.
     */
    public GeoLocationDataException getException() {
        return exception;
    }

    /**
     * @return The parsed GeoLocation object if the waypoint was successfully
     *         parsed.
     */
    public T getGeoLocationObject() {
        return geoObject;
    }

    /**
     * @return The artifact that was parsed or failed in parsing (could be
     *         null).
     */
    public BlackboardArtifact getArtifact() {
        return artifact;
    }
}
