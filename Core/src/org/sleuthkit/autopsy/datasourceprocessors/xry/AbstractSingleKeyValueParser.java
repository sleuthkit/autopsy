/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Template parse method for reports that make blackboard attributes from a
 * single key value pair.
 *
 * This parse implementation will create 1 artifact per XRY entity.
 */
abstract class AbstractSingleKeyValueParser implements XRYFileParser {
    
    private static final Logger logger = Logger.getLogger(AbstractSingleKeyValueParser.class.getName());

    private static final char KEY_VALUE_DELIMITER = ':';
    
    protected static final String PARSER_NAME = "XRY DSP";

    @Override
    public void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at [ %s ]", reportPath.toString()));

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            String[] xryLines = xryEntity.split("\n");

            List<BlackboardAttribute> attributes = new ArrayList<>();

            //First line of the entity is the title.
            if (xryLines.length > 0) {
                logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));
            }

            String namespace = "";
            //Process each line, searching for a key value pair or a namespace.
            //If neither are found, an error message is logged.
            for (int i = 1; i < xryLines.length; i++) {
                String xryLine = xryLines[i];

                String candidateNamespace = xryLine.trim();
                //Check if the line is a namespace, which gives context to the keys
                //that follow.
                if (isNamespace(candidateNamespace)) {
                    namespace = candidateNamespace;
                    continue;
                }

                //Find the XRY key on this line. Assume key is the value between
                //the start of the line and the first delimiter.
                int keyDelimiter = xryLine.indexOf(KEY_VALUE_DELIMITER);
                if (keyDelimiter == -1) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] Expected a key value "
                            + "pair on this line (in brackets) [ %s ], but one was not detected."
                            + " Here is the previous line [ %s ]. What does this mean?", xryLine, xryLines[i - 1]));
                    continue;
                }
                String key = xryLine.substring(0, keyDelimiter).trim();
                String value = xryLine.substring(keyDelimiter + 1).trim();

                if (!isKey(key)) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] The following key, "
                            + "value pair (in brackets, respectively) [ %s ], [ %s ] was not recognized. Discarding..."
                            + " Here is the previous line [ %s ] for context. What does this key mean?", key, value, xryLines[i - 1]));
                    continue;
                }

                if (value.isEmpty()) {
                    logger.log(Level.SEVERE, String.format("[XRY DSP] The following key "
                            + "(in brackets) [ %s ] was recognized, but the value was empty. Discarding..."
                            + " Here is the previous line for context [ %s ]. What does this mean?", key, xryLines[i - 1]));
                    continue;
                }

                BlackboardAttribute attribute = makeAttribute(namespace, key, value);
                //Temporarily allowing null to be valid return type until a decision
                //is made about how to handle keys we are choosing to ignore.
                if (attribute != null) {
                    attributes.add(makeAttribute(namespace, key, value));
                }
            }

            //Only create artifacts with non-empty attributes.
            if (!attributes.isEmpty()) {
                makeArtifact(attributes, parent);
            }
        }
    }

    /**
     * Determines if the key candidate is a known key. A key candidate is a
     * string literal that begins a line and is terminated by a semi-colon.
     *
     * Ex:
     *
     * Call Type : Missed
     *
     * "Call Type" would be the key candidate that was extracted.
     *
     * @param key Key to test. These keys are trimmed of whitespace only.
     * @return Indication if this key can be processed.
     */
    abstract boolean isKey(String key);

    /**
     * Determines if the namespace candidate is a known namespace. A namespace
     * candidate is a string literal that makes up an entire line.
     *
     * Ex:
     *
     * To 
     * Tel : +1245325
     *
     * "To" would be the candidate namespace that was extracted.
     *
     * @param nameSpace Namespace to test. Namespaces are trimmed of whitespace
     * only.
     * @return Indication if this namespace can be processed.
     */
    abstract boolean isNamespace(String nameSpace);

    /**
     * Creates an attribute from the extracted key value pair.
     * 
     * @param nameSpace The namespace of this key value pair.
     * It will have been verified with isNamespace, otherwise it will be empty.
     * @param key The key that was verified with isKey.
     * @param value The value associated with that key.
     * @return
     */
    abstract BlackboardAttribute makeAttribute(String nameSpace, String key, String value);

    /**
     * Makes an artifact from the parsed attributes.
     */
    abstract void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException;

}
