/*
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
package org.sleuthkit.autopsy.timeline.utils;

import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class TimelineDBUtils {

    private final SleuthkitCase sleuthkitCase;

    public TimelineDBUtils(SleuthkitCase sleuthkitCase) {
        this.sleuthkitCase = sleuthkitCase;
    }

    public String csvAggFunction(String args) {
        return (sleuthkitCase.getDatabaseType() == TskData.DbType.POSTGRESQL ? "string_agg" : "group_concat")
               + "(Cast (" + args + " AS VARCHAR) , '" + "," + "')";
    }
}
