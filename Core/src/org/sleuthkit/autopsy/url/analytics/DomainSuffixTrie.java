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
package org.sleuthkit.autopsy.url.analytics;

import com.google.common.annotations.Beta;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.url.analytics.Trie.TrieResult;

@Beta
public class DomainSuffixTrie {

    private static Iterable<String> getSuffixIter(String host) {
        // parse the tokens splitting on delimiter
        List<String> tokens = Stream.of(host.toLowerCase().split(DELIMITER))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        Collections.reverse(tokens);
        return tokens;
    }

    //private void Node get
    // Character for joining domain segments.
    private static final String JOINER = ".";
    // delimiter when used with regex for domains
    private static final String DELIMITER = "\\" + JOINER;

    private final Trie<String, String> trie = new Trie<>();

    /**
     *
     * @param suffix
     * @param leaf
     */
    public void add(String suffix, String leaf) {
        this.trie.add(getSuffixIter(suffix), leaf);
    }

    /**
     * Determines if the host is a known type of host. If so, returns the
     * portion of the host suffix that signifies the domain type (i.e.
     * "hotmail.com" or "mail.google.com") and the domain type. Also returned in
     * the DomainCategoryResult is whether or not any children of the found node
     * in the trie and consequently, whether or not 
     *
     * @param host The host.
     * @return The DomainCategoryResult if a portion of the suffix was found
     * 
     * 
     * A pair of the host suffix and domain type for that suffix if
     * found. Otherwise, returns null.
     */
    public DomainCategoryResult findHostCategory(String host) {
        // if no host, return none.
        if (StringUtils.isBlank(host)) {
            return null;
        }

        TrieResult<String, String> result = this.trie.getDeepest(getSuffixIter(host));
        List<String> keys = new ArrayList<>(result.getKeys());
        Collections.reverse(keys);
        String suffix = String.join(JOINER, keys);
        return new DefaultDomainCategoryResult(suffix, result.getValue(), result.hasChildren());
    }
}
