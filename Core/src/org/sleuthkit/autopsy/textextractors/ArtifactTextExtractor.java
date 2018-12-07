/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.apache.commons.io.IOUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extracts text from artifacts by concatenating the values of all of the
 * artifact's attributes.
 */
class ArtifactTextExtractor<T extends Content> extends ContentTextExtractor<T> {

    static final private Logger logger = Logger.getLogger(ArtifactTextExtractor.class.getName());

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void logWarning(final String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS  }
    }

    private InputStream getInputStream(Content artifact) throws TextExtractorException {
                        BlackboardArtifact art = (BlackboardArtifact)artifact;

        // Concatenate the string values of all attributes into a single
        // "content" string to be indexed.
        StringBuilder artifactContents = new StringBuilder();

        Content dataSource = null;
        try {
            dataSource = art.getDataSource();
        } catch (TskCoreException tskCoreException) {
            throw new TextExtractorException("Unable to get datasource for artifact: " + artifact.toString(), tskCoreException);
        }
        if (dataSource == null) {
            throw new TextExtractorException("Datasource was null for artifact: " + artifact.toString());
        }

        try {
            for (BlackboardAttribute attribute : art.getAttributes()) {
                artifactContents.append(attribute.getAttributeType().getDisplayName());
                artifactContents.append(" : ");
                // We have also discussed modifying BlackboardAttribute.getDisplayString()
                // to magically format datetime attributes but that is complicated by
                // the fact that BlackboardAttribute exists in Sleuthkit data model
                // while the utility to determine the timezone to use is in ContentUtils
                // in the Autopsy datamodel.
                switch (attribute.getValueType()) {
                    case DATETIME:
                        artifactContents.append(ContentUtils.getStringTime(attribute.getValueLong(), dataSource));
                        break;
                    default:
                        artifactContents.append(attribute.getDisplayString());
                }
                artifactContents.append(System.lineSeparator());
            }
        } catch (TskCoreException tskCoreException) {
            throw new TextExtractorException("Unable to get attributes for artifact: " + artifact.toString(), tskCoreException);
        }

        return IOUtils.toInputStream(artifactContents, StandardCharsets.UTF_8);
    }

    @Override
    public Reader getReader(Content source) throws TextExtractorException {
        return new InputStreamReader(getInputStream(source), StandardCharsets.UTF_8);
    }

    @Override
    public long getID(Content source) {
        BlackboardArtifact art = (BlackboardArtifact)source;
        return art.getArtifactID();
    }

    @Override
    public String getName(Content source) {
                BlackboardArtifact art = (BlackboardArtifact)source;
        return art.getDisplayName() + "_" + art.getArtifactID();
    }

    @Override
    public void setExtractionSettings(ExtractionContext context) {
    }

    @Override
    public boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isSupported(Content file, String detectedFormat) {
        return true;
    }
}
