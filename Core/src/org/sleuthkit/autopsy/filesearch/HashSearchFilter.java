/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionListener;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException;

/**
 *
 */
class HashSearchFilter extends AbstractFileSearchFilter<HashSearchPanel> {

    private static final String EMPTY_HASH_MESSAGE = NbBundle
            .getMessage(HashSearchFilter.class, "HashSearchPanel.emptyHashMsg.text");

    public HashSearchFilter() {
        this(new HashSearchPanel());
    }

    public HashSearchFilter(HashSearchPanel component) {
        super(component);
    }

    @Override
    public boolean isEnabled() {
        return (this.getComponent().getMd5HashCheckBox().isSelected()
                || this.getComponent().getSha256HashCheckBox().isSelected());
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String predicate = "";
        if (this.getComponent().getMd5HashCheckBox().isSelected()) {
            String md5Hash = this.getComponent().getMd5TextField().getText();

            if (md5Hash.isEmpty()) {
                throw new FilterValidationException(EMPTY_HASH_MESSAGE);
            }
            predicate = "md5 = '" + md5Hash.toLowerCase() + "'"; //NON-NLS
        }
        
        if (this.getComponent().getSha256HashCheckBox().isSelected()) {
            String sha256Hash = this.getComponent().getSha256TextField().getText();

            if (sha256Hash.isEmpty()) {
                throw new FilterValidationException(EMPTY_HASH_MESSAGE);
            }
            if (predicate.isEmpty()) {
                predicate = "sha256 = '" + sha256Hash.toLowerCase() + "'"; //NON-NLS
            } else {
                predicate = "( " + predicate + " AND sha256 = '" + sha256Hash.toLowerCase() + "')"; //NON-NLS
            }
        }

        return predicate;
    }

    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addActionListener(l);
    }

    @Override
    @Messages({
        "HashSearchFilter.errorMessage.emptyHash=Hash data is empty.",
        "# {0} - hash data length", 
        "HashSearchFilter.errorMessage.wrongLengthMd5=Input length({0}), doesn''t match the MD5 length(32).",
        "# {0} - hash data length", 
        "HashSearchFilter.errorMessage.wrongLengthSha256=Input length({0}), doesn''t match the SHA-256 length(64).",
        "HashSearchFilter.errorMessage.wrongCharacter=MD5 contains invalid hex characters."
    })
    public boolean isValid() {
        if (this.getComponent().getMd5HashCheckBox().isSelected()) {
            String inputHashData = this.getComponent().getMd5TextField().getText();
            if (inputHashData.isEmpty()) {
                setLastError(Bundle.HashSearchFilter_errorMessage_emptyHash());
                return false;
            }
            if (inputHashData.length() != 32) {
                setLastError(Bundle.HashSearchFilter_errorMessage_wrongLengthMd5(inputHashData.length()));
                return false;
            }
            if (!inputHashData.matches("[0-9a-fA-F]+")) {
                setLastError(Bundle.HashSearchFilter_errorMessage_wrongCharacter());
                return false;
            }
        }
        
        if (this.getComponent().getSha256HashCheckBox().isSelected()) {
            String inputHashData = this.getComponent().getSha256TextField().getText();
            if (inputHashData.isEmpty()) {
                setLastError(Bundle.HashSearchFilter_errorMessage_emptyHash());
                return false;
            }
            if (inputHashData.length() != 64) {
                setLastError(Bundle.HashSearchFilter_errorMessage_wrongLengthSha256(inputHashData.length()));
                return false;
            }
            if (!inputHashData.matches("[0-9a-fA-F]+")) {
                setLastError(Bundle.HashSearchFilter_errorMessage_wrongCharacter());
                return false;
            }
        }
        
        return true;
    }
}
