/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.apache.solr.common.util.ContentStreamBase.StringStream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * An implementation of the KeywordSearchService interface that uses
 * Solr for text indexing and search.
 */
public class SolrSearchService implements KeywordSearchService {
    @Override
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (artifact == null)
            return;
        
        Case currentCase = Case.getCurrentCase();
        if (currentCase == null)
            return;
        
        SleuthkitCase sleuthkitCase = currentCase.getSleuthkitCase();
        if (sleuthkitCase == null)
            return;
        
        AbstractFile abstractFile = sleuthkitCase.getAbstractFileById(artifact.getObjectID());
        if (abstractFile == null)
            return;
        
        Content dataSource = abstractFile.getDataSource();
        if (dataSource == null)
            return;
        
        // Concatenate the string values of all attributes into a single 
        // "content" string to be indexed.
        String artifactContents = "";
        
        for (BlackboardAttribute attribute : artifact.getAttributes()) {
            artifactContents += attribute.getDisplayString();
            artifactContents += " ";
        }
        
        if (artifactContents.isEmpty())
            return;
        
        // To play by the rules of the existing text markup implementations,
        // we need to (a) index the artifact contents in a "chunk" and 
        // (b) create a separate index entry for the base artifact.
        // We distinguish artifact content from file content by applying a 
        // mask to the artifact id to make its value > 0x8000000000000000 (i.e. negative).

        // First, create an index entry for the base artifact.
        HashMap<String, String> solrFields = new HashMap<>();
        String documentId = Long.toString(0x8000000000000000L + artifact.getArtifactID());

        solrFields.put(Server.Schema.ID.toString(), documentId);
        
        // Set the IMAGE_ID field.
        solrFields.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));

        try {
            Ingester.getDefault().ingest(new StringStream(""), solrFields, 0);
        }
        catch (Ingester.IngesterException ex) {
        }

        // Next create the index entry for the document content.
        // The content gets added to a single chunk. We may need to add chunking
        // support later.
        long chunkId = 1;

        documentId += "_" + Long.toString(chunkId);
        solrFields.replace(Server.Schema.ID.toString(), documentId);
        
        StringStream contentStream = new StringStream(artifactContents);
        
        try {
            Ingester.getDefault().ingest(contentStream, solrFields, contentStream.getSize());
        }
        catch (Ingester.IngesterException ex) {
        }
    }

    @Override
    public void close() throws IOException {
    }
}
