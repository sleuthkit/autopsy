/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Attempts to get the domain from a url/domain provided removing the
 * subdomain(s).
 */
class DomainTokenizer {

    /**
     * This is a node in the trie. Children in the hashmap are identified by
     * token. So for example, for a domain google.co.uk, the top level category
     * would have an entry for "uk" linking to a domain category with "co".
     */
    private static class DomainCategory extends HashMap<String, DomainCategory> {

        private DomainCategory getOrAddChild(String childKey) {
            DomainCategory cat = this.get(childKey);
            if (cat == null) {
                cat = new DomainCategory();
                this.put(childKey, cat);
            }

            return cat;
        }
    }

    // Character for joining domain segments.
    private static final String JOINER = ".";
    // delimiter when used with regex
    private static final String DELIMITER = "\\" + JOINER;
    
    private static final String WILDCARD = "*";
    private static final String EXCEPTION_PREFIX = "!";

    // taken from https://publicsuffix.org/list/public_suffix_list.dat
    // file containing line seperated suffixes
    // rules for parsing can be found here: https://publicsuffix.org/list/
    private static final String DOMAIN_LIST = "public_suffix_list.dat";

    // token for comments
    private static final String COMMENT_TOKEN = "//";

    // singleton instance of this class.
    private static DomainTokenizer categorizer = null;

    /**
     * Returns the singleton instance of this class.
     *
     * @return The DomainCategorizer instance.
     * @throws IOException
     */
    static DomainTokenizer getInstance() throws IOException {
        if (categorizer == null) {
            categorizer = load();
        }

        return categorizer;
    }

    /**
     * Loads a DomainCategorizer instance using the public suffix list.
     *
     * @return The DomainCategorizer instance.
     * @throws IOException If there is an error reading the file.
     */
    private static DomainTokenizer load() throws IOException {
        try (InputStream is = DomainTokenizer.class.getResourceAsStream(DOMAIN_LIST);
                InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isReader)) {

            DomainTokenizer categorizer = new DomainTokenizer();
            while (reader.ready()) {
                String line = reader.readLine();
                String trimmed = line.trim();
                if (!StringUtils.isBlank(trimmed) && !trimmed.startsWith(COMMENT_TOKEN)) {
                    categorizer.addDomainSuffix(trimmed);
                }
            }

            return categorizer;
        }
    }

    private DomainTokenizer() {
    }

    // The top-level trie node.
    private final DomainCategory trie = new DomainCategory();

    /**
     * Parses a domain suffix and adds components to the trie in reverse order
     * (i.e. ".co.uk" becomes "uk" -> "co").
     *
     * @param domainSuffix The domain suffix.
     */
    private void addDomainSuffix(String domainSuffix) {
        if (StringUtils.isBlank(domainSuffix)) {
            return;
        }

        String[] tokens = domainSuffix.toLowerCase().trim().split(DELIMITER);

        DomainCategory cat = trie;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i];
            if (StringUtils.isBlank(token)) {
                continue;
            }
            
            cat = cat.getOrAddChild(tokens[i]);
        }
    }

    /**
     * Gets the domain by attempting to identify the host without the subdomain.
     * If no domain can be determined, the domain is returned.
     *
     * @param domain The domain to query for.
     * @return If provided argument is blank, null is returned. If no domain
     * suffixes can be identified, the full host is returned. If a host and
     * suffixes are identified, the domain (all suffixes with a prefix of the
     * next token) are returned.
     */
    String getDomain(String domain) {
        if (StringUtils.isBlank(domain)) {
            return "";
        }

        List<String> tokens = Stream.of(domain.toLowerCase().split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        int idx = tokens.size() - 1;
        DomainCategory cat = trie;

        for (; idx >= 0; idx--) {
            // an exception rule must be at the beginning of a suffix, and, in 
            // practice, indicates a domain that would otherwise be a further
            // suffix with a wildcard rule per: https://publicsuffix.org/list/
            if (cat.get(EXCEPTION_PREFIX + tokens.get(idx)) != null) {
                break;
            }
            
            DomainCategory newCat = cat.get(tokens.get(idx));
            
            // if no matching token can be found, look for wildcard token
            if (newCat == null) {
                // if no wildcard token can be found, the portion found 
                // so far is the suffix.
                newCat = cat.get(WILDCARD);
                if (newCat == null) {
                    break;   
                }
            }
            
            cat = newCat;
        }

        // if first suffix cannot be found, return the whole domain
        if (idx == tokens.size() - 1) {
            return domain;
        } else {
            int minIndex = Math.max(0, idx);
            List<String> subList = tokens.subList(minIndex, tokens.size());
            return String.join(JOINER, subList);
        }
    }
}
