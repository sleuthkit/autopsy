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
 * Search parameters for accounts.
 */
public class AccountSearchParams extends DataArtifactSearchParam {

    private final String accountType;

    /**
     * Main constructor.
     *
     * @param accountType  The account type identifier.
     * @param dataSourceId The data source id to filter on or null.
     */
    public AccountSearchParams(String accountType, Long dataSourceId) {
        super(BlackboardArtifact.Type.TSK_ACCOUNT, dataSourceId);
        this.accountType = accountType;
    }

    /**
     * @return The account type identifier.
     */
    public String getAccountType() {
        return accountType;
    }
}