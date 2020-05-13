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

/**
 * The result of attempting to parse a GeoLocation object.
 */
public class GeoLocationParseResult<T> {

    private final boolean successfullyParsed;
    private final GeoLocationDataException exception;
    private final T geoObject;

    private GeoLocationParseResult(boolean successfullyParsed, GeoLocationDataException exception, T geoObject) {
        this.successfullyParsed = successfullyParsed;
        this.exception = exception;
        this.geoObject = geoObject;
    }

    /**
     * Creates a GeoLocationParseResult as the result of a successful parse.
     *
     * @param <T>      The type of object to parse.
     * @param geoObject The GeoLocation object to include in result.
     *
     * @return The generated WaypointParseResult.
     */
    public static <T> GeoLocationParseResult<T> create(T geoObject) {
        if (geoObject == null) {
            return new GeoLocationParseResult(false, new GeoLocationDataException("GeoLocation object provided was null"), null);
        }

        return new GeoLocationParseResult(true, null, geoObject);
    }

    /**
     * Creates a GeoLocationParseResult indicating a failed parsing.
     *
     * @param <T>       The type of GeoLocation object that was supposed to be parsed.
     * @param exception The exception generated.
     *
     * @return The GeoLocationParseResult indicating an error.
     */
    public static <T> GeoLocationParseResult<T> error(GeoLocationDataException exception) {
        return new GeoLocationParseResult(false, exception, null);
    }

    /**
     * Whether or not the GeoLocation object has been successfully parsed. If true, there
     * should be a non-null GeoLocation object present.  Otherwise, there should be a non-null exception.
     *
     * @return Whether or not the GeoLocation object has been successfully parsed.
     */
    public boolean isSuccessfullyParsed() {
        return successfullyParsed;
    }

    /**
     * @return The exception caused in attempting to parse the GeoLocation object if there was an exception.
     */
    public GeoLocationDataException getException() {
        return exception;
    }

    /**
     * @return The parsed GeoLocation object if the waypoint was successfully parsed.
     */
    public T getGeoLocationObject() {
        return geoObject;
    }
}
