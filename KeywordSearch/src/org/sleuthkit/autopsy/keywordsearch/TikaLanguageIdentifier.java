/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.logging.Level;

import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * TextLanguageIdentifier implementation based on a wrapped Tike
 * LanguageIdentifier
 */
class TikaLanguageIdentifier implements TextLanguageIdentifier {

    private static final Logger logger = Logger.getLogger(TikaLanguageIdentifier.class.getName());
    private static final int MIN_STRING_LENGTH = 1000;

    @Override
    public void addLanguageToBlackBoard(String extracted, AbstractFile sourceFile) {
        if (extracted.length() > MIN_STRING_LENGTH) {
            org.apache.tika.language.LanguageIdentifier li = new org.apache.tika.language.LanguageIdentifier(extracted);

            //logger.log(Level.INFO, sourceFile.getName() + " detected language: " + li.getLanguage()
            //        + " with " + ((li.isReasonablyCertain()) ? "HIGH" : "LOW") + " confidence");

            BlackboardArtifact genInfo;
            try {
                genInfo = sourceFile.getGenInfoArtifact();

                BlackboardAttribute textLang = new BlackboardAttribute(
                        BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT_LANGUAGE.getTypeID(),
                        KeywordSearchIngestModule.MODULE_NAME, li.getLanguage());

                genInfo.addAttribute(textLang);

            } catch (TskCoreException ex) {
                logger.log(Level.WARNING,
                           "failed to add TSK_TEXT_LANGUAGE attribute to TSK_GEN_INFO artifact for file: " + sourceFile
                                   .getName(), ex);
            }

        }
    }
}