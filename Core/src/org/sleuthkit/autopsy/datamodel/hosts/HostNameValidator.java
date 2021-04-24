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
package org.sleuthkit.autopsy.datamodel.hosts;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Host;

/**
 * Provides methods for validating host names.
 */
public class HostNameValidator {

    /**
     * Gets the validation message based on the current text checked against the
     * host names.
     *
     * @param curName The current name in the text field.
     * @param initialName If editing a name, the initial name of the host.
     * Otherwise, null can be provided for this parameter.
     * @param currentHostsTrimmedUpper The current host names. This set should
     * be sanitized to upper case and trimmed.
     * @return The validation message if the name is not valid or null.
     */
    @NbBundle.Messages({
        "HostNameValidator_getValidationMessage_onEmpty=Please provide some text for the host name.",
        "HostNameValidator_getValidationMessage_sameAsOriginal=Please provide a new name for this host.",
        "HostNameValidator_getValidationMessage_onDuplicate=Another host already has the same name.  Please choose a different name.",})
    public static String getValidationMessage(String curName, String initialName, Set<String> currentHostsTrimmedUpper) {

        if (StringUtils.isBlank(curName)) {
            return Bundle.HostNameValidator_getValidationMessage_onEmpty();
        }

        if (StringUtils.equalsIgnoreCase(initialName, curName)) {
            return Bundle.HostNameValidator_getValidationMessage_sameAsOriginal();
        }

        if (currentHostsTrimmedUpper.contains(curName.trim().toUpperCase())) {
            return Bundle.HostNameValidator_getValidationMessage_onDuplicate();
        }

        return null;
    }

    /**
     * Generates a list of host names trimmed and to upper case that can be used
     * with getValidationMessage.
     *
     * @param hosts The hosts.
     * @return The set of host names trimmed and to upper case.
     */
    public static Set<String> getSanitizedHostNames(Collection<Host> hosts) {
        Stream<Host> hostsStream = hosts != null ? hosts.stream() : Stream.empty();
        return hostsStream
                .map(h -> h == null ? null : h.getName())
                .filter(hName -> StringUtils.isNotBlank(hName))
                .map(hName -> hName.trim().toUpperCase())
                .collect(Collectors.toSet());
    }
}
