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

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;

/**
 * Executable sub-node filters.
 */
public enum FileExtExecutableFilter implements FileExtSearchFilter {
    ExecutableFilter_EXE(0, "ExecutableFilter_EXE", ".exe", ImmutableSet.of(".exe")), //NON-NLS
    ExecutableFilter_DLL(1, "ExecutableFilter_DLL", ".dll", ImmutableSet.of(".dll")), //NON-NLS
    ExecutableFilter_BAT(2, "ExecutableFilter_BAT", ".bat", ImmutableSet.of(".bat")), //NON-NLS
    ExecutableFilter_CMD(3, "ExecutableFilter_CMD", ".cmd", ImmutableSet.of(".cmd")), //NON-NLS
    ExecutableFilter_COM(4, "ExecutableFilter_COM", ".com", ImmutableSet.of(".com"));
    //NON-NLS
    final int id;
    final String name;
    final String displayName;
    final Set<String> filter;

    private FileExtExecutableFilter(int id, String name, String displayName, Set<String> filter) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.filter = filter;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getId() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public Set<String> getFilter() {
        return this.filter;
    }
    
}
