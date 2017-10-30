/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
 * @author pmartel
 */
class NameSearchFilter extends AbstractFileSearchFilter<NameSearchPanel> {

    private static final String EMPTY_NAME_MESSAGE = NbBundle
            .getMessage(NameSearchFilter.class, "NameSearchFilter.emptyNameMsg.text");

    public NameSearchFilter() {
        this(new NameSearchPanel());
    }

    public NameSearchFilter(NameSearchPanel component) {
        super(component);
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().getNameCheckBox().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String keyword = this.getComponent().getSearchTextField().getText();

        if (keyword.isEmpty()) {
            throw new FilterValidationException(EMPTY_NAME_MESSAGE);
        }

        keyword.replace("'", "''"); // escape quotes in string        
        //TODO: escaping might not be enough, would ideally be part of a prepared statement

        return "LOWER(name) LIKE LOWER('%" + keyword + "%')"; //NON-NLS
    }

    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addActionListener(l);
    }

    @Override
    @Messages ({
        "NameSearchFilter.errorMessage.emtpyName=Please input a name to search."
    })
    public boolean isValid() {
        if(this.getComponent().getSearchTextField().getText().isEmpty()) {
            setLastError(Bundle.NameSearchFilter_errorMessage_emtpyName());
            return false;
        }
        return true;
    }
}
