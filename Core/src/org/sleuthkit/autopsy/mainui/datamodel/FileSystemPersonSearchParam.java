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

import java.util.Objects;

/**
 * Key for person object in order to retrieve data from DAO.
 */
public class FileSystemPersonSearchParam {
    private final Long personObjectId;

    /**
     * Create search param.
     * 
     * @param personObjectId May be null to fetch hosts not associated with a Person
     */
    public FileSystemPersonSearchParam(Long personObjectId) {
        this.personObjectId = personObjectId;
    }

    public Long getPersonObjectId() {
        return personObjectId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.personObjectId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileSystemPersonSearchParam other = (FileSystemPersonSearchParam) obj;
        if (!Objects.equals(this.personObjectId, other.personObjectId)) {
            return false;
        }
        return true;
    }
}
