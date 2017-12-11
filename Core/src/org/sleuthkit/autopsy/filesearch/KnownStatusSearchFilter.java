/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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
import org.sleuthkit.datamodel.TskData.FileKnown;

/**
 * Filters on the known status of a file (known/unknown/known-bad)
 */
class KnownStatusSearchFilter extends AbstractFileSearchFilter<KnownStatusSearchPanel> {

    private static final String NONE_SELECTED_MESSAGE = NbBundle
            .getMessage(KnownStatusSearchFilter.class, "KnownStatusSearchFilter.noneSelectedMsg.text");

    KnownStatusSearchFilter(KnownStatusSearchPanel panel) {
        super(panel);
    }

    KnownStatusSearchFilter() {
        this(new KnownStatusSearchPanel());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().getKnownCheckBox().isSelected();
                
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        KnownStatusSearchPanel panel = this.getComponent();

        boolean unknown = panel.getUnknownOptionCheckBox().isSelected();
        boolean known = panel.getKnownOptionCheckBox().isSelected();
        boolean knownBad = panel.getKnownBadOptionCheckBox().isSelected();

        if (!(unknown || known || knownBad)) {
            throw new FilterValidationException(NONE_SELECTED_MESSAGE);
        }

        String expr = "NULL";
        if (unknown) {
            expr += " OR " + predicateHelper(FileKnown.UNKNOWN); //NON-NLS
        }
        if (known) {
            expr += " OR " + predicateHelper(FileKnown.KNOWN); //NON-NLS
        }
        if (knownBad) {
            expr += " OR " + predicateHelper(FileKnown.BAD); //NON-NLS
        }
        return expr;
    }

    /**
     * Make the predicate fragment for a file known status
     *
     * @param knownStatus status for the file to match
     *
     * @return un-padded SQL boolean expression
     */
    private String predicateHelper(FileKnown knownStatus) {
        return "known = " + knownStatus.getFileKnownValue(); //NON-NLS
    }

    @Override
    public void addActionListener(ActionListener l) {
    }

    @Override
    @Messages ({
        "KnownStatusSearchFilter.errorMessage.noKnownStatusCheckboxSelected=At least one known status checkbox must be selected."
    })
    public boolean isValid() {
        if (!this.getComponent().isValidSearch()) {
            setLastError(Bundle.KnownStatusSearchFilter_errorMessage_noKnownStatusCheckboxSelected());
            return false;
        }
        return true;
    }
}
