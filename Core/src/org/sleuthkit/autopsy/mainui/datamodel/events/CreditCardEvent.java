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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Event for new email messages.
 */
public class CreditCardEvent extends DataArtifactEvent {

    private final String binPrefix;
    private final boolean rejectedStatus;

    /**
     * Main constructor.
     *
     * @param binPrefix       The bin prefix of the credit card.
     * @param includeRejected Whether or not to include rejected items in search
     *                        results.
     * @param dataSourceId    The data source id or null for no data source
     *                        filtering.
     */
    public CreditCardEvent(String binPrefix, boolean rejectedStatus, long dataSourceId) {
        super(BlackboardArtifact.Type.TSK_ACCOUNT, dataSourceId);
        this.binPrefix = binPrefix;
        this.rejectedStatus = rejectedStatus;
    }

    public String getBinPrefix() {
        return binPrefix;
    }

    public boolean isRejectedStatus() {
        return rejectedStatus;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.binPrefix);
        hash = 61 * hash + (this.rejectedStatus ? 1 : 0);
        hash = 61 * hash + super.hashCode();
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
        final CreditCardEvent other = (CreditCardEvent) obj;
        if (this.rejectedStatus != other.rejectedStatus) {
            return false;
        }
        if (!Objects.equals(this.binPrefix, other.binPrefix)) {
            return false;
        }
        return super.equals(obj);
    }
}
