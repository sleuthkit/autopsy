/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel.persons;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Person;

/**
 * Provides methods for validating person names.
 */
public class PersonNameValidator {

    /**
     * Gets the validation message based on the current text checked against the
     * person names.
     *
     * @param curName The current name to be validated.
     * @param initialName If editing a name, the initial name of the person.
     * Otherwise, null can be provided for this parameter.
     * @param currentPersonsTrimmedUpper The current person names. This set should
     * be sanitized to upper case and trimmed.
     * @return The validation message if the name is not valid or null.
     */
    @NbBundle.Messages({
        "PersonNameValidator_getValidationMessage_onEmpty=Please provide some text for the person name.",
        "PersonNameValidator_getValidationMessage_sameAsOriginal=Please provide a new name for this person.",
        "PersonNameValidator_getValidationMessage_onDuplicate=Another person already has the same name.  Please choose a different name.",})
    public static String getValidationMessage(String curName, String initialName, Set<String> currentPersonsTrimmedUpper) {

        if (StringUtils.isBlank(curName)) {
            return Bundle.PersonNameValidator_getValidationMessage_onEmpty();
        }

        if (StringUtils.equalsIgnoreCase(initialName, curName)) {
            return Bundle.PersonNameValidator_getValidationMessage_sameAsOriginal();
        }

        if (currentPersonsTrimmedUpper.contains(curName.trim().toUpperCase())) {
            return Bundle.PersonNameValidator_getValidationMessage_onDuplicate();
        }

        return null;
    }

    /**
     * Generates a list of person names trimmed and to upper case that can be used
     * with getValidationMessage.
     *
     * @param persons The persons.
     * @return The set of person names trimmed and to upper case.
     */
    public static Set<String> getSanitizedPersonNames(Collection<Person> persons) {
        Stream<Person> personsStream = persons != null ? persons.stream() : Stream.empty();
        return personsStream
                .map(h -> h == null ? null : h.getName())
                .filter(hName -> StringUtils.isNotBlank(hName))
                .map(hName -> hName.trim().toUpperCase())
                .collect(Collectors.toSet());
    }
}
