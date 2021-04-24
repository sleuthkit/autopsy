/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import javax.swing.JButton;
import javax.swing.JLabel;

/**
 * A data bag for the persona searching thread. It wraps the account id to
 * search for, and the UI label and button to update once the search completes.
 */
class AccountPersonaSearcherData {

    // Account identifier to search personas for.
    private final String accountIdentifer;
    // Persona name label to be updated when the search is complete.
    private final JLabel personaNameLabel;
    // Persona action button to be updated when the search is complete
    private final JButton personaActionButton;

    /**
     * Constructor.
     *
     * @param accountIdentifer Account identifier.
     * @param personaNameLabel Persona name label.
     * @param personaActionButton Persona button.
     */
    AccountPersonaSearcherData(String accountIdentifer, JLabel personaNameLabel, JButton personaActionButton) {
        this.accountIdentifer = accountIdentifer;
        this.personaNameLabel = personaNameLabel;
        this.personaActionButton = personaActionButton;
    }

    /**
     * Gets the account identifier.
     *
     * @return Account identifier.
     */
    public String getAccountIdentifer() {
        return accountIdentifer;
    }

    /**
     * Gets the persona name label.
     *
     * @return Persona name label.
     */
    public JLabel getPersonaNameLabel() {
        return personaNameLabel;
    }

    /**
     * Gets the persona button.
     *
     * @return Persona button.
     */
    public JButton getPersonaActionButton() {
        return personaActionButton;
    }
}
