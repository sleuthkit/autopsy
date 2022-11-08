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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizer;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizerException;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * The default domain category provider that makes use of the default csv
 * resource. This implementation is used if no other DomainCategorizer can
 * determine a category for a host/domain entry.
 *
 * CSV entries describing these domain types are compiled from sources. webmail:
 * https://github.com/mailcheck/mailcheck/wiki/List-of-Popular-Domains
 * disposable mail: https://www.npmjs.com/package/disposable-email-domains
 * messaging:
 * https://www.raymond.cc/blog/list-of-web-messengers-for-your-convenience/
 *
 * NOTE: The (at)SuppressWarnings("try") on the class is to suppress warnings
 * relating to the fact that the close method can throw an InterruptedException
 * since Exception can encompass the InterruptedException. See the following
 * github issue and bugs for more information:
 * https://github.com/joyent/java-manta/issues/322#issuecomment-326391239,
 * https://bugs.openjdk.java.net/browse/JDK-8155591,
 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=350279.
 */
@SuppressWarnings("try")
public class DefaultDomainCategorizer implements DomainCategorizer {

    private static final String COMMENT_PREFIX = "#";
    private static final String CSV_DELIMITER = ",";
    private static final String DOMAIN_TYPE_CSV = "default_domain_categories.csv"; //NON-NLS
    private static final Logger logger = Logger.getLogger(DefaultDomainCategorizer.class.getName());

    /**
     * Loads the domain suffixes from the csv resource file into a mapping of
     * domain suffix to category name.
     *
     * @return The mapping.
     * @throws IOException
     */
    private static Map<String, String> loadMapping() throws IOException {
        try (InputStream is = DomainCategoryRunner.class.getResourceAsStream(DOMAIN_TYPE_CSV);
                InputStreamReader isReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(isReader)) {

            Map<String, String> mapping = new HashMap<>();
            int lineNum = 1;
            while (reader.ready()) {
                String line = reader.readLine();
                if (!StringUtils.isBlank(line) && !line.startsWith(COMMENT_PREFIX)) {
                    addItem(mapping, line.trim(), lineNum);
                    lineNum++;
                }
            }

            return mapping;
        }
    }

    /**
     * Adds a mapping of domain suffix to category based on the csv line found
     * in the file.
     *
     * @param mapping The suffix to category mapping.
     * @param line The line to be parsed.
     * @param lineNumber The line number of this csv line.
     */
    private static void addItem(Map<String, String> mapping, String line, int lineNumber) {
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

        // determine the host
        String hostSuffix = csvItems[0];
        if (StringUtils.isBlank(hostSuffix)) {
            logger.log(Level.WARNING, String.format("Could not determine host suffix for this line: \"%s\" at line %d", line, lineNumber));
            return;
        }

        mapping.put(hostSuffix.toLowerCase(), domainTypeStr);
    }

    // the host suffix to category mapping.
    private Map<String, String> mapping = null;

    @Override
    public synchronized void initialize() throws DomainCategorizerException {
        if (isInitialized()) {
            return;
        }

        try {
            this.mapping = loadMapping();
        } catch (IOException ex) {
            throw new DomainCategorizerException("Unable to load domain type csv for domain category analysis", ex);
        }
    }

    /**
     * Returns true if this categorizer is properly initialized.
     *
     * @return True if this categorizer is properly initialized.
     */
    private synchronized boolean isInitialized() {
        return this.mapping != null;
    }

    @Override
    public synchronized DomainCategory getCategory(String domain, String host) throws DomainCategorizerException {
        if (!isInitialized()) {
            initialize();
        }

        // use host; use domain as fallback if no host provided
        String hostToUse = StringUtils.isBlank(host) ? domain : host;

        if (StringUtils.isBlank(hostToUse)) {
            return null;
        }

        // split the host into tokens and find longest matching suffix 
        // (or return null if not found)
        List<String> tokens = Arrays.asList(hostToUse.split("\\."));
        for (int i = 0; i < tokens.size(); i++) {
            String searchString = String.join(".", tokens.subList(i, tokens.size()));
            String category = mapping.get(searchString);
            if (StringUtils.isNotBlank(category)) {
                return new DomainCategory(searchString, category);
            }
        }

        return null;
    }

    @Override
    public synchronized void close() throws Exception {
        // clear out the mapping to release resources
        mapping = null;
    }
}
