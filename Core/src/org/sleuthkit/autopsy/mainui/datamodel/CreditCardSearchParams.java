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

import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Base credit card search params.
 */
public class CreditCardSearchParams extends DataArtifactSearchParam {

    private static final String TYPE_ID = "CREDIT_CARD";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final boolean rejectedIncluded;

    /**
     * Main constructor.
     *
     * @param includeRejected Whether or not to include rejected items in search
     *                        results.
     * @param dataSourceId    The data source id or null for no data source
     *                        filtering.
     */
    public CreditCardSearchParams(boolean rejectedIncluded, Long dataSourceId) {
        super(BlackboardArtifact.Type.TSK_ACCOUNT, dataSourceId);
        this.rejectedIncluded = rejectedIncluded;
    }

    public boolean isRejectedIncluded() {
        return rejectedIncluded;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.rejectedIncluded ? 1 : 0);
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
        final CreditCardSearchParams other = (CreditCardSearchParams) obj;
        if (this.rejectedIncluded != other.rejectedIncluded) {
            return false;
        }
        return true;
    }

}
