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
import java.util.Optional;
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
    
    protected static final String PARSER_NAME = "XRY DSP";

    @Override
    public void parse(XRYFileReader reader, Content parent) throws IOException, TskCoreException {
        Path reportPath = reader.getReportPath();
        logger.log(Level.INFO, String.format("[XRY DSP] Processing report at [ %s ]", reportPath.toString()));

        while (reader.hasNextEntity()) {
            String xryEntity = reader.nextEntity();
            String[] xryLines = xryEntity.split("\n");

            List<BlackboardAttribute> attributes = new ArrayList<>();

            //First line of the entity is the title, the entity will always be non-empty.
            logger.log(Level.INFO, String.format("[XRY DSP] Processing [ %s ]", xryLines[0]));

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
                if(!XRYKeyValuePair.isPair(xryLine)) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] Expected a key value "
                            + "pair on this line (in brackets) [ %s ], but one was not detected.", 
                            xryLine));
                    continue;
                }
                
                XRYKeyValuePair pair = XRYKeyValuePair.from(xryLine, namespace);

                if (!canProcess(pair)) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] The following key, "
                            + "value pair (in brackets) [ %s ] was not recognized. Discarding...",
                            pair));
                    continue;
                }

                if (pair.getValue().isEmpty()) {
                    logger.log(Level.WARNING, String.format("[XRY DSP] The following key value pair"
                            + "(in brackets) [ %s ] was recognized, but the value was empty. Discarding...", 
                            pair));
                    continue;
                }

                //Create the attribute, if any.
                Optional<BlackboardAttribute> attribute = getBlackboardAttribute(namespace, pair);
                if(attribute.isPresent()) {
                    attributes.add(attribute.get());
                }
            }

            //Only create artifacts with non-empty attributes.
            if (!attributes.isEmpty()) {
                makeArtifact(attributes, parent);
            }
        }
    }

    /**
     * Determines if the XRY key value pair can be processed by the 
     * XRY report parser.
     * 
     * @param pair
     * @return 
     */
    abstract boolean canProcess(XRYKeyValuePair pair);

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
     * @param pair The XRYKeyValuePair to process
     * @return The corresponding blackboard attribute, if any.
     */
    abstract Optional<BlackboardAttribute> getBlackboardAttribute(String nameSpace, XRYKeyValuePair pair);

    /**
     * Makes an artifact from the parsed attributes.
     */
    abstract void makeArtifact(List<BlackboardAttribute> attributes, Content parent) throws TskCoreException;

}
