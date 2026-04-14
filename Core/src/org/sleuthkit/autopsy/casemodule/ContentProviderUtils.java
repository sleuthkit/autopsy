/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.util.Collection;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.ContentStreamProvider;

/**
 * Utility methods for matching and loading AutopsyContentProvider instances.
 *
 * Providers that follow the BASENAME_X.Y.Z naming convention support
 * version-compatible case opening: a case can be opened with any installed
 * module whose base name matches and whose patch level (Z) is greater than or
 * equal to the one recorded when the case was created, provided the
 * major.minor (X.Y) is identical.
 */
class ContentProviderUtils {

    private ContentProviderUtils() {
    }

    /**
     * Parses the version triplet (X.Y.Z) from a provider name following the
     * BASENAME_X.Y.Z convention.
     *
     * @param name The provider name.
     * @return An Optional containing {major, minor, patch}, or empty if the
     *         name does not end with a valid three-part version suffix.
     */
    static Optional<int[]> parseProviderVersion(String name) {
        if (name == null) {
            return Optional.empty();
        }
        int lastUnderscore = name.lastIndexOf('_');
        if (lastUnderscore < 0 || lastUnderscore == name.length() - 1) {
            return Optional.empty();
        }
        String[] parts = name.substring(lastUnderscore + 1).split("\\.");
        if (parts.length < 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            });
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses the base name from a BASENAME_X.Y.Z provider name.
     *
     * @param name The provider name.
     * @return An Optional containing the base name, or empty if the name does
     *         not carry a valid version suffix.
     */
    static Optional<String> parseProviderBaseName(String name) {
        if (!parseProviderVersion(name).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(name.substring(0, name.lastIndexOf('_')));
    }

    /**
     * Returns true if the module's provider version is compatible with the
     * version recorded when the case was created. Requires matching base names
     * and major.minor (X.Y), and a module patch level (Z) greater than or
     * equal to the created patch.
     *
     * @param createdName The provider name stored in the .aut file.
     * @param moduleName  The installed provider's getName() value.
     * @return True if compatible.
     */
    static boolean isVersionCompatible(String createdName, String moduleName) {
        Optional<int[]> created = parseProviderVersion(createdName);
        Optional<int[]> module = parseProviderVersion(moduleName);
        if (!created.isPresent() || !module.isPresent()) {
            return false;
        }
        Optional<String> createdBase = parseProviderBaseName(createdName);
        Optional<String> moduleBase = parseProviderBaseName(moduleName);
        if (!createdBase.isPresent() || !moduleBase.isPresent()
                || !StringUtils.equalsIgnoreCase(createdBase.get(), moduleBase.get())) {
            return false;
        }
        int[] c = created.get();
        int[] m = module.get();
        return c[0] == m[0] && c[1] == m[1] && m[2] >= c[2];
    }

    /**
     * Returns the installed AutopsyContentProvider whose base name matches the
     * base name parsed from createdName, or empty if no such module is
     * installed. This is used in the error path to distinguish "wrong version
     * installed" from "module not installed at all"; version compatibility is
     * not checked.
     *
     * @param createdName The provider name stored in the .aut file.
     * @return The installed provider with a matching base name, or empty.
     */
    static Optional<AutopsyContentProvider> findInstalledProvider(String createdName) {
        Optional<String> baseName = parseProviderBaseName(createdName);
        if (!baseName.isPresent()) {
            return Optional.empty();
        }
        Collection<? extends AutopsyContentProvider> providers
                = Lookup.getDefault().lookupAll(AutopsyContentProvider.class);
        for (AutopsyContentProvider provider : providers) {
            Optional<String> providerBase = parseProviderBaseName(
                    provider != null ? provider.getName() : null);
            if (providerBase.isPresent()
                    && StringUtils.equalsIgnoreCase(baseName.get(), providerBase.get())) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a loaded ContentStreamProvider for the given created name, or
     * null if no installed provider matches. Accepts an exact name match or a
     * version-compatible match for providers following the BASENAME_X.Y.Z
     * convention.
     *
     * @param createdName The provider name stored in the case .aut file.
     * @return The loaded ContentStreamProvider, or null if none matched.
     */
    static ContentStreamProvider getContentProvider(String createdName) {
        Collection<? extends AutopsyContentProvider> providers
                = Lookup.getDefault().lookupAll(AutopsyContentProvider.class);
        for (AutopsyContentProvider provider : providers) {
            if (provider != null
                    && (StringUtils.equalsIgnoreCase(createdName, provider.getName())
                        || isVersionCompatible(createdName, provider.getName()))) {
                ContentStreamProvider contentProvider = provider.load();
                if (contentProvider != null) {
                    return contentProvider;
                }
            }
        }
        return null;
    }
}
