/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import org.apache.commons.io.IOUtils;
import org.apache.solr.common.util.ContentStream;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import static org.sleuthkit.autopsy.keywordsearch.Bundle.ByteArtifactStream_getSrcInfo_text;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class ArtifactExtractor extends TextExtractor<Void, BlackboardArtifact> {

    static Content getDataSource(BlackboardArtifact artifact) throws TskCoreException {
        Content dataSource;
        Case currentCase;
        try {
            currentCase = Case.getCurrentCase();
        } catch (IllegalStateException ignore) {
            // thorown by Case.getCurrentCase() if currentCase is null
            return null;
        }

        SleuthkitCase sleuthkitCase = currentCase.getSleuthkitCase();
        if (sleuthkitCase == null) {
            return null;
        }

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
    boolean noExtractionOptionsAreEnabled() {
        return false;
    }

    @Override
    void logWarning(String msg, Exception ex) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    Void newAppendixProvider() {
        return null;
    }

    @Override
    InputStream getInputStream(BlackboardArtifact artifact) {

        // Concatenate the string values of all attributes into a single
        // "content" string to be indexed.
        StringBuilder artifactContents = new StringBuilder();
        Content dataSource;
        try {
            dataSource = getDataSource(artifact);
            if (dataSource == null) {
                return null;
            }

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
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        if (artifactContents.length() == 0) {
            return null;
        }


        return IOUtils.toInputStream(artifactContents);

    }

    @Override
    Reader getReader(InputStream stream, BlackboardArtifact source, Void appendix) throws Ingester.IngesterException {
        return new InputStreamReader(stream);
    }

    @Override
    long getID(BlackboardArtifact source) {
        return source.getArtifactID();
    }



    @Override
    String getName(BlackboardArtifact source) {
        return source.getDisplayName();
    }

    static private class ByteArtifactStream implements ContentStream {

        //input
        private final byte[] content; //extracted subcontent
        private long contentSize;
        private final BlackboardArtifact aContent; //origin

        private final InputStream stream;

        private static final Logger logger = Logger.getLogger(ByteArtifactStream.class.getName());

        public ByteArtifactStream(byte[] content, long contentSize, BlackboardArtifact aContent) {
            this.content = content;
            this.aContent = aContent;
            stream = new ByteArrayInputStream(content, 0, (int) contentSize);
        }

        public byte[] getByteContent() {
            return content;
        }

        public BlackboardArtifact getSourceContent() {
            return aContent;
        }

        @Override
        public String getContentType() {
            return "text/plain;charset=" + Server.DEFAULT_INDEXED_TEXT_CHARSET.name(); //NON-NLS
        }

        @Override
        public String getName() {
            return aContent.getDisplayName();
        }

        @Override
        public Reader getReader() throws IOException {
            return new InputStreamReader(stream);

        }

        @Override
        public Long getSize() {
            return contentSize;
        }

        @Override
        @NbBundle.Messages("ByteArtifactStream.getSrcInfo.text=Artifact:{0}")
        public String getSourceInfo() {
            return ByteArtifactStream_getSrcInfo_text(aContent.getArtifactID());
        }

        @Override
        public InputStream getStream() throws IOException {
            return stream;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            stream.close();
        }
    }
}
