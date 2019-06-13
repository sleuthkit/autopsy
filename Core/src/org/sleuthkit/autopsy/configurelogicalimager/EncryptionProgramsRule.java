/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configurelogicalimager;

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
public final class EncryptionProgramsRule {

    private static final String ENCRYPTION_PROGRAMS_RULE_NAME = Bundle.EncryptionProgramsRule_encryptionProgramsRuleName();
    private static final String ENCRYPTION_PROGRAMS_RULE_DESCRIPTION = Bundle.EncryptionProgramsRule_encryptionProgramsRuleDescription();
    private static final List<String> FILENAMES = new ArrayList<>();
    
    private EncryptionProgramsRule() {}
    
    // TODO: Add more files here
    static {
        FILENAMES.add("truecrypt.exe"); //NON-NLS
        FILENAMES.add("AxCrypt.exe"); // NON-NLS
        FILENAMES.add("VeraCrypt.exe"); // NON-NLS
        FILENAMES.add("VeraCrypt Format.exe"); // NON-NLS
        FILENAMES.add("VeraCrypt Setup.exe"); // NON-NLS
        FILENAMES.add("VeraCryptExpander.exe"); // NON-NLS
    }

    public static String getName() {
        return ENCRYPTION_PROGRAMS_RULE_NAME;
    }

    public static String getDescription() {
        return ENCRYPTION_PROGRAMS_RULE_DESCRIPTION;
    }
    
    public static List<String> getFilenames() {
        return FILENAMES;
    }
}
