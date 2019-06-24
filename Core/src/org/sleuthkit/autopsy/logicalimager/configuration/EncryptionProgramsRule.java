/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.configuration;

import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;

/**
 * Encryption programs rule
 */
@NbBundle.Messages({
    "EncryptionProgramsRule.encryptionProgramsRuleName=Encryption Programs",
    "EncryptionProgramsRule.encryptionProgramsRuleDescription=Find encryption programs"
})
final class EncryptionProgramsRule {

    private static final String ENCRYPTION_PROGRAMS_RULE_NAME = Bundle.EncryptionProgramsRule_encryptionProgramsRuleName();
    private static final String ENCRYPTION_PROGRAMS_RULE_DESCRIPTION = Bundle.EncryptionProgramsRule_encryptionProgramsRuleDescription();
    private static final List<String> FILENAMES = new ArrayList<>();

    private EncryptionProgramsRule() {
        //private no arg constructor intentionally blank
    }

    static {
        // Truecrypt
        FILENAMES.add("truecrypt.exe"); // NON-NLS

        // AxCrypt
        FILENAMES.add("AxCrypt.exe"); // NON-NLS

        // VeraCrypt
        FILENAMES.add("VeraCrypt.exe"); // NON-NLS
        FILENAMES.add("VeraCrypt Format.exe"); // NON-NLS
        FILENAMES.add("VeraCrypt Setup.exe"); // NON-NLS
        FILENAMES.add("VeraCryptExpander.exe"); // NON-NLS

        // GnuPG
        FILENAMES.add("gpg-agent.exe"); // NON-NLS
        FILENAMES.add("gpg-connect-agent.exe"); // NON-NLS
        FILENAMES.add("gpg-preset-passphrase.exe"); // NON-NLS
        FILENAMES.add("gpg-wks-client.exe"); // NON-NLS
        FILENAMES.add("gpg.exe"); // NON-NLS
        FILENAMES.add("gpgconf.exe"); // NON-NLS
        FILENAMES.add("gpgme-w32spawn.exe"); // NON-NLS
        FILENAMES.add("gpgsm.exe"); // NON-NLS
        FILENAMES.add("gpgtar.exe"); // NON-NLS
        FILENAMES.add("gpgv.exe"); // NON-NLS

        // Symantec Encryption Desktop aka PGP
        FILENAMES.add("PGP Viewer.exe"); // NON-NLS
        FILENAMES.add("PGPcbt64.exe"); // NON-NLS
        FILENAMES.add("PGPdesk.exe"); // NON-NLS
        FILENAMES.add("PGPfsd.exe"); // NON-NLS
        FILENAMES.add("PGPmnApp.exe"); // NON-NLS
        FILENAMES.add("pgpnetshare.exe"); // NON-NLS
        FILENAMES.add("pgpp.exe"); // NON-NLS
        FILENAMES.add("PGPpdCreate.exe"); // NON-NLS
        FILENAMES.add("pgppe.exe"); // NON-NLS
        FILENAMES.add("pgpstart.exe"); // NON-NLS
        FILENAMES.add("PGPtray.exe"); // NON-NLS
        FILENAMES.add("PGPwde.exe"); // NON-NLS
        FILENAMES.add("PGP Portable.exe"); // NON-NLS

    }

    static String getName() {
        return ENCRYPTION_PROGRAMS_RULE_NAME;
    }

    static String getDescription() {
        return ENCRYPTION_PROGRAMS_RULE_DESCRIPTION;
    }

    static List<String> getFilenames() {
        return FILENAMES;
    }
}
