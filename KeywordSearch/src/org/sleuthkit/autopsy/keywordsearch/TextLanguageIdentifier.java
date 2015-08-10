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

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 *
 */
interface TextLanguageIdentifier {

    /**
     * attempts to identify the language of the given String and add it to the
     * black board for the given {@code AbstractFile} as a TSK_TEXT_LANGUAGE
     * attribute on a TSK_GEN_INFO artifact.
     *
     * @param extracted  the String whose language is to be identified
     * @param sourceFile the AbstractFile the string is extracted from.
     *
     * @return
     */
    public void addLanguageToBlackBoard(String extracted, AbstractFile sourceFile);
}
