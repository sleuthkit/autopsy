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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.apache.commons.io.IOUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extracts text from artifacts by concatenating the values of all of the
 * artifact's attributes.
 */
class ArtifactTextExtractor implements TextExtractor<BlackboardArtifact> {

    static final private Logger logger = Logger.getLogger(ArtifactTextExtractor.class.getName());

    /**
     * Get the Content that is the data source for the given artifact. //JMTODO:
     * is there a prexisting method to do this?
     *
     * @param artifact
     *
     * @return The data source for the given artifact as a Content object, or
     *         null if it could not be found.
     *
     * @throws TskCoreException if there is a problem accessing the case db.
     */
    static Content getDataSource(BlackboardArtifact artifact) throws TskCoreException {

        Case currentCase;
        try {
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ignore) {
            // thorown by Case.getOpenCase() if currentCase is null
            return null;
        }

        SleuthkitCase sleuthkitCase = currentCase.getSleuthkitCase();
        if (sleuthkitCase == null) {
            return null;

        }
        Content dataSource;
        AbstractFile abstractFile = sleuthkitCase.getAbstractFileById(artifact.getObjectID());
        if (abstractFile != null) {
            dataSource = abstractFile.getDataSource();
        } else {
            dataSource = sleuthkitCase.getContentById(artifact.getObjectID());
        }

        if (dataSource == null) {
            return null;
        }
        return dataSource;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void logWarning(final String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS  }
    }

    private InputStream getInputStream(BlackboardArtifact artifact) throws TextExtractorException {
        // Concatenate the string values of all attributes into a single
        // "content" string to be indexed.
        StringBuilder artifactContents = new StringBuilder();

        Content dataSource = null;
        try {
            dataSource = getDataSource(artifact);
        } catch (TskCoreException tskCoreException) {
            throw new TextExtractorException("Unable to get datasource for artifact: " + artifact.toString(), tskCoreException);
        }
        if (dataSource == null) {
            throw new TextExtractorException("Datasource was null for artifact: " + artifact.toString());
        }

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
    public Reader getReader(BlackboardArtifact source) throws TextExtractorException {
        return new InputStreamReader(getInputStream(source), StandardCharsets.UTF_8);
    }

    @Override
    public long getID(BlackboardArtifact source) {
        return source.getArtifactID();
    }

    @Override
    public String getName(BlackboardArtifact source) {
        return source.getDisplayName() + "_" + source.getArtifactID();
    }
}
