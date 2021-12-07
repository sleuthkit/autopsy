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

import org.openide.util.NbBundle;

/**
 * Filters for deleted content
 */
@NbBundle.Messages({"DeletedContent.fsDelFilter.text=File System",
    "DeletedContent.allDelFilter.text=All"})
public enum DeletedContentFilter {

    /**
     * Names are used in sql query so make sure they are sql friendly.
     */
    FS_DELETED_FILTER(0, "FS_DELETED_FILTER", Bundle.DeletedContent_fsDelFilter_text()),
    ALL_DELETED_FILTER(1, "ALL_DELETED_FILTER", Bundle.DeletedContent_allDelFilter_text());

    private int id;
    private String name;
    private String displayName;

    private DeletedContentFilter(int id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;

    }

    public String getName() {
        return this.name;
    }

    public int getId() {
        return this.id;
    }

    public String getDisplayName() {
        return this.displayName;
    }
}
