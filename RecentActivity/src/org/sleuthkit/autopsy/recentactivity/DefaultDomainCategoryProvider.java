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
package org.sleuthkit.autopsy.recentactivity;

import org.sleuthkit.autopsy.url.analytics.DomainSuffixTrie;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.url.analytics.DomainCategoryProvider;
import org.sleuthkit.autopsy.url.analytics.DomainCategoryResult;

/**
 * The default domain category provider that makes use of the default csv
 * resource.
 */
class DefaultDomainCategoryProvider implements DomainCategoryProvider {

    // csv delimiter
    private static final String CSV_DELIMITER = ",";
    private static final String DOMAIN_TYPE_CSV = "default_domain_categories.csv"; //NON-NLS
    private static final Logger logger = Logger.getLogger(DefaultDomainCategoryProvider.class.getName());

    /**
     * Loads the trie of suffixes from the csv resource file.
     *
     * @return The root trie node.
     * @throws IOException
     */
    private static DomainSuffixTrie loadTrie() throws IOException {
        try (InputStream is = DomainCategorizer.class.getResourceAsStream(DOMAIN_TYPE_CSV);
                InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isReader)) {

            DomainSuffixTrie trie = new DomainSuffixTrie();
            int lineNum = 1;
            while (reader.ready()) {
                String line = reader.readLine();
                if (!StringUtils.isBlank(line)) {
                    addItem(trie, line.trim(), lineNum);
                    lineNum++;
                }
            }

            return trie;
        }
    }

    /**
     * Adds a trie node based on the csv line.
     *
     * @param trie The root trie node.
     * @param line The line to be parsed.
     * @param lineNumber The line number of this csv line.
     */
    private static void addItem(DomainSuffixTrie trie, String line, int lineNumber) {
        // make sure this isn't a blank line.
        if (StringUtils.isBlank(line)) {
            return;
        }

        String[] csvItems = line.split(CSV_DELIMITER);
        // line should be a key value pair
        if (csvItems.length < 2) {
            logger.log(Level.WARNING, String.format("Unable to properly parse line of \"%s\" at line %d", line, lineNumber));
            return;
        }

        // determine the domain type from the value, and return if can't be determined.
        String domainTypeStr = csvItems[1].trim();
        if (StringUtils.isBlank(domainTypeStr)) {
            logger.log(Level.WARNING, String.format("No category specified for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        // gather the domainSuffix and parse into domain trie tokens
        String hostSuffix = csvItems[0];
        if (StringUtils.isBlank(hostSuffix)) {
            logger.log(Level.WARNING, String.format("Could not determine host suffix for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        trie.add(hostSuffix, domainTypeStr);
    }

    // the root node for the trie containing suffixes for domain categories.
    private DomainSuffixTrie trie = null;

    @Override
    public void initialize() throws IngestModuleException {
        if (this.trie == null) {
            try {
                this.trie = loadTrie();
            } catch (IOException ex) {
                throw new IngestModule.IngestModuleException("Unable to load domain type csv for domain category analysis", ex);
            }
        }
    }

    @Override
    public DomainCategoryResult getCategory(String domain, String host) {
        return trie.findHostCategory(host);
    }
}
