/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.geolocation;

/**
 *
 * An exception call for Exceptions that occure in the geolocation dialog.  
 */
public class GeoLocationUIException extends Exception{
    private static final long serialVersionUID = 1L;

    /**
     * Create exception containing the error message
     *
     * @param msg the message
     */
    public GeoLocationUIException(String msg) {
        super(msg);
    }

    /**
     * Create exception containing the error message and cause exception
     *
     * @param msg the message
     * @param ex  cause exception
     */
    public GeoLocationUIException(String msg, Exception ex) {
        super(msg, ex);
    }
}
