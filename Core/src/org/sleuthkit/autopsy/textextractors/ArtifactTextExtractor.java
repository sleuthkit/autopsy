/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extracts text from artifacts by concatenating the values of all of the
 * artifact's attributes.
 */
class ArtifactTextExtractor implements TextExtractor {

    private final BlackboardArtifact artifact;

    public ArtifactTextExtractor(BlackboardArtifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public Reader getReader() throws InitReaderException {
        // Concatenate the string values of all attributes into a single
        // "content" string to be indexed.
        StringBuilder artifactContents = new StringBuilder();

        try {
            for (BlackboardAttribute attribute : artifact.getAttributes()) {
                artifactContents.append(attribute.getAttributeType().getDisplayName());
                artifactContents.append(" : ");
                // We have also discussed modifying BlackboardAttribute.getDisplayString()
                // to magically format datetime attributes but that is complicated by
                // the fact that BlackboardAttribute exists in Sleuthkit data model
                // while the utility to determine the timezone to use is in ContentUtils
                // in the Autopsy datamodel.
                switch (attribute.getValueType()) {
                    case DATETIME:
                        artifactContents.append(TimeZoneUtils.getFormattedTime(attribute.getValueLong()));
                        break;
                    default:
                        artifactContents.append(attribute.getDisplayString());
                }
                artifactContents.append(System.lineSeparator());
            }
        } catch (TskCoreException tskCoreException) {
            throw new InitReaderException("Unable to get attributes for artifact: " + artifact.toString(), tskCoreException);
        }

        return new InputStreamReader(IOUtils.toInputStream(artifactContents,
                StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Override
    public boolean isSupported() {
        return true;
    }
}
