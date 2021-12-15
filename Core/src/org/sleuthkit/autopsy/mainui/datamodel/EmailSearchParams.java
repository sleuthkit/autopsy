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
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Search parameters for email messages.
 */
public class EmailSearchParams extends DataArtifactSearchParam {

    private final String account;
    private final String folder;

    /**
     * Main constructor.
     *
     * @param dataSourceId The data source id or null if no data source
     *                     filtering should occur.
     * @param account      The email account.
     * @param folder       The folder within the email account.
     */
    public EmailSearchParams(Long dataSourceId, String account, String folder) {
        super(BlackboardArtifact.Type.TSK_EMAIL_MSG, dataSourceId);
        this.account = account;
        this.folder = folder;
    }

    public String getAccount() {
        return account;
    }

    public String getFolder() {
        return folder;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.account);
        hash = 23 * hash + Objects.hashCode(this.folder);
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
        final EmailSearchParams other = (EmailSearchParams) obj;
        if (!Objects.equals(this.account, other.account)) {
            return false;
        }
        if (!Objects.equals(this.folder, other.folder)) {
            return false;
        }
        return true;
    }

}
