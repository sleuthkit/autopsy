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
        
        // Set the ID field for the document. We distinguish artifact
        // content from file content by prepending "art-" to the
        // artifact object id.
        HashMap<String, String> solrFields = new HashMap<>();
        solrFields.put(Server.Schema.ID.toString(), "art-" + artifact.getArtifactID());
        
        // Set the IMAGE_ID field.
        solrFields.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));
        
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
