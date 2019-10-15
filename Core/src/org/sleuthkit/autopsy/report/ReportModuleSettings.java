/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.io.Serializable;

/**
 * An interface for defining report module settings. The settings are
 * serializable to support persistence of settings for different configurations
 * and between invocations of the application. Report module writers will
 * implement their own settings classes conforming to this interface.
 */
public interface ReportModuleSettings extends Serializable {

    /**
     * Returns the version number of the settings object. The version number
     * should be a private final static long per the documentation of the
     * Serializable interface.
     *
     * @return A serialization version number.
     */
    long getVersionNumber();
}
