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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.Arrays;
import java.util.Optional;

/**
 * Language.
 *
 * Contents which are detected to have these languages should be indexed to a corresponding language-specific field
 * such as content_ja.
 */
public enum Language {
    JAPANESE("ja");

    private String value;

    String getValue() {
        return value;
    }

    static Optional<Language> fromValue(String value) {
        return Arrays.stream(Language.values()).filter(x -> x.value.equals(value)).findFirst();
    }

    Language(String value) {
        this.value = value;
    }
}
