/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
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

                // This is ugly since it will need to updated any time a new
                // TSK_DATETIME_* attribute is added. A slightly less ugly
                // alternative would be to assume that all date time attributes
                // will have a name of the form "TSK_DATETIME*" and check
                // attribute.getAttributeTypeName().startsWith("TSK_DATETIME*".
                // The major problem with that approach is that it would require
                // a round trip to the database to get the type name string.
                // We have also discussed modifying BlackboardAttribute.getDisplayString()
                // to magically format datetime attributes but that is complicated by
                // the fact that BlackboardAttribute exists in Sleuthkit data model
                // while the utility to determine the timezone to use is in ContentUtils
                // in the Autopsy datamodel.
                if (attribute.getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {

                    artifactContents.append(ContentUtils.getStringTime(attribute.getValueLong(), dataSource));
                } else {
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

        // To play by the rules of the existing text markup implementations,
        // we need to (a) index the artifact contents in a "chunk" and
        // (b) create a separate index entry for the base artifact.
        // We distinguish artifact content from file content by applying a
        // mask to the artifact id to make its value > 0x8000000000000000 (i.e. negative).
        // First, create an index entry for the base artifact.
        HashMap<String, String> solrFields = new HashMap<>();
        String documentId = Long.toString(artifact.getArtifactID());

        solrFields.put(Server.Schema.ID.toString(), documentId);

        // Set the IMAGE_ID field.
        solrFields.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));

        // Next create the index entry for the document content.
        // The content gets added to a single chunk. We may need to add chunking
        // support later.
        long chunkId = 1;

        documentId += "_" + Long.toString(chunkId);
        solrFields.replace(Server.Schema.ID.toString(), documentId);

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
    ContentStream getContentStream(byte[] encodedBytes, int length, BlackboardArtifact source) {
        return new ByteArtifactStream(encodedBytes, length, source);
    }

    @Override
    ContentStream getNullStream(BlackboardArtifact source) {
        return new Ingester.NullArtifactStream(source);
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
