/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.textclassifier;

import com.google.common.collect.ImmutableSet;

/**
 * Class containing the mime types that are supported by the text classifier.
 */
public final class SupportedFormats {

    private SupportedFormats() {
        //This is a utility class that doesn't need to be instantiated.
    }
    
    /**
     * Document types for text classification.
     */
    private static final ImmutableSet<String> DOCUMENT_MIME_TYPES = new ImmutableSet.Builder<String>()
            .add("text/plain", //NON-NLS
                    "text/css", //NON-NLS
                    "text/html", //NON-NLS
                    "text/csv", //NON-NLS
                    "text/xml", //NON-NLS
                    "text/x-log", //NON-NLS
                    "application/rtf", //NON-NLS
                    "application/pdf", //NON-NLS
                    "application/json", //NON-NLS
                    "application/javascript", //NON-NLS
                    "application/xml", //NON-NLS
                    "application/xhtml+xml", //NON-NLS
                    "application/x-msoffice", //NON-NLS
                    "application/x-ooxml", //NON-NLS
                    "application/msword", //NON-NLS
                    "application/msword2", //NON-NLS
                    "application/vnd.wordperfect", //NON-NLS
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", //NON-NLS
                    "application/vnd.ms-powerpoint", //NON-NLS
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", //NON-NLS
                    "application/vnd.ms-excel", //NON-NLS
                    "application/vnd.ms-excel.sheet.4", //NON-NLS
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", //NON-NLS
                    "application/vnd.oasis.opendocument.presentation", //NON-NLS
                    "application/vnd.oasis.opendocument.spreadsheet", //NON-NLS
                    "application/vnd.oasis.opendocument.text", //NON-NLS
                    "message/rfc822" //NON-NLS
            ).build();

    public static boolean contains(String mimeType) {
        return DOCUMENT_MIME_TYPES.contains(mimeType);
    }

    static ImmutableSet<String> getDocumentMIMETypes() {
        return DOCUMENT_MIME_TYPES;
    }
}
