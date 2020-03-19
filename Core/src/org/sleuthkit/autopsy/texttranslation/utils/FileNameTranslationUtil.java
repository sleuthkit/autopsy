/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.texttranslation.utils;

import org.apache.commons.io.FilenameUtils;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
 * A utility to translate file names.
 */
public final class FileNameTranslationUtil {

    /**
     * Translates a file name using the configured machine translation service.
     *
     * @param fileName The file name.
     *
     * @return The translation of the file name.
     *
     * @throws NoServiceProviderException If machine translation is not
     *                                    configured.
     * @throws TranslationException       If there is an error doing the
     *                                    translation.
     */
    public static String translate(String fileName) throws NoServiceProviderException, TranslationException {
        /*
         * Don't attempt translation if the characters of the file name are all
         * ASCII chars.
         *
         * TODO (Jira-6175): This filter prevents translation of many
         * non-English file names composed entirely of Latin chars.
         */
        if (fileName.matches("^\\p{ASCII}+$")) {
            return "";
        }

        TextTranslationService translator = TextTranslationService.getInstance();
        String baseName = FilenameUtils.getBaseName(fileName);
        String translation = translator.translate(baseName);
        if (!translation.isEmpty()) {
            String extension = FilenameUtils.getExtension(fileName);
            if (!extension.isEmpty()) {
                String extensionDelimiter = (extension.isEmpty()) ? "" : ".";
                translation += extensionDelimiter + extension;
            }
        }
        return translation;
    }

    /**
     * Prevent instantiation of this utility class
     */
    private FileNameTranslationUtil() {
    }

}
