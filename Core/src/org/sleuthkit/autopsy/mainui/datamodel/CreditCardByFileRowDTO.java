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

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Row of credit card information for a file.
 */
@Messages({
    "CreditCardByFileRow_file_displayName=File",
    "CreditCardByFileRow_accounts_displayName=Accounts",
    "CreditCardByFileRow_status_displayName=Status"
})
public class CreditCardByFileRowDTO extends BaseRowDTO {

    private static ColumnKey getColumnKey(String displayName) {
        return new ColumnKey(displayName.toUpperCase().replaceAll("\\s", "_"), displayName, "");
    }

    static List<ColumnKey> COLUMNS = ImmutableList.of(
            getColumnKey(Bundle.CreditCardByFileRow_file_displayName()),
            getColumnKey(Bundle.CreditCardByFileRow_accounts_displayName()),
            getColumnKey(Bundle.CreditCardByFileRow_status_displayName())
    );

    private static final String TYPE_ID = "CREDIT_CARD_BY_FILE";

    public static String getTypeIdForClass() {
        return TYPE_ID;
    }

    private final AbstractFile file;
    private final Set<BlackboardArtifact> associatedArtifacts;
    private final String fileName;
    private final long accounts;
    private final Set<BlackboardArtifact.ReviewStatus> statuses;
    private final String reviewStatusString;

    /**
     * Main constructor.
     *
     * @param file                The file where credit cards were found.
     * @param associatedArtifacts The associated artifacts.
     * @param fileName            The name of the file to display in columns.
     * @param accounts            The number of accounts to display in columns.
     * @param statuses            The associated statuses.
     * @param reviewStatusString  The review status string to display.
     */
    CreditCardByFileRowDTO(AbstractFile file, Set<BlackboardArtifact> associatedArtifacts, String fileName, long accounts,
            Set<BlackboardArtifact.ReviewStatus> statuses, String reviewStatusString) {
        super(ImmutableList.of(fileName, accounts, reviewStatusString), TYPE_ID, file.getId());
        this.file = file;
        this.associatedArtifacts = associatedArtifacts;
        this.fileName = fileName;
        this.accounts = accounts;
        this.statuses = statuses;
        this.reviewStatusString = reviewStatusString;
    }

    public AbstractFile getFile() {
        return file;
    }

    public Set<BlackboardArtifact> getAssociatedArtifacts() {
        return associatedArtifacts;
    }

    public String getFileName() {
        return fileName;
    }

    public long getAccounts() {
        return accounts;
    }

    public Set<BlackboardArtifact.ReviewStatus> getStatuses() {
        return statuses;
    }

    public String getReviewStatusString() {
        return reviewStatusString;
    }
}
