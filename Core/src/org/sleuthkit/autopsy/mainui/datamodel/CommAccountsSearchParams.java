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
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Key for accessing data about communication accounts from the DAO.
 */
public class CommAccountsSearchParams extends DataArtifactSearchParam {

    private static final String TYPE_ID = "DATA_ARTIFACT_ACCOUNT";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final Account.Type type;
    private final Long dataSourceId;

    public CommAccountsSearchParams(Account.Type type, Long dataSourceId) {
        super(BlackboardArtifact.Type.TSK_ACCOUNT, dataSourceId);
        this.type = type;
        this.dataSourceId = dataSourceId;
    }

    public Account.Type getType() {
        return type;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.type);
        hash = 23 * hash + Objects.hashCode(this.dataSourceId);
        hash = 23 * hash + super.hashCode();
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
        final CommAccountsSearchParams other = (CommAccountsSearchParams) obj;
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return super.equals(obj);
    }

}
