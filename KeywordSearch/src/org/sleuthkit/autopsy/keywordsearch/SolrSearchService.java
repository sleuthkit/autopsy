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

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.MissingResourceException;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.util.ContentStreamBase.StringStream;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProvider(service = KeywordSearchService.class)
public class SolrSearchService implements KeywordSearchService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occurred when talking to server"; //NON-NLS
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection"; //NON-NLS
    private static final int IS_REACHABLE_TIMEOUT_MS = 1000;

    @Override
    public void indexArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (artifact == null) {
            return;
        }

        // We only support artifact indexing for Autopsy versions that use
        // the negative range for artifact ids.
        long artifactId = artifact.getArtifactID();

        if (artifactId > 0) {
            return;
        }

        Case currentCase;
        try {
            currentCase = Case.getCurrentCase();
        } catch (IllegalStateException ignore) {
            // thorown by Case.getCurrentCase() if currentCase is null
            return;
        }

        SleuthkitCase sleuthkitCase = currentCase.getSleuthkitCase();
        if (sleuthkitCase == null) {
            return;
        }

        Content dataSource;
        AbstractFile abstractFile = sleuthkitCase.getAbstractFileById(artifact.getObjectID());
        if (abstractFile != null) {
            dataSource = abstractFile.getDataSource();
        } else {
            dataSource = sleuthkitCase.getContentById(artifact.getObjectID());
        }

        if (dataSource == null) {
            return;
        }

        // Concatenate the string values of all attributes into a single 
        // "content" string to be indexed.
        StringBuilder artifactContents = new StringBuilder();

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
            if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()
                    || attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()) {

                artifactContents.append(ContentUtils.getStringTime(attribute.getValueLong(), dataSource));
            } else {
                artifactContents.append(attribute.getDisplayString());
            }
            artifactContents.append(System.lineSeparator());
        }

        if (artifactContents.length() == 0) {
            return;
        }

        // To play by the rules of the existing text markup implementations,
        // we need to (a) index the artifact contents in a "chunk" and 
        // (b) create a separate index entry for the base artifact.
        // We distinguish artifact content from file content by applying a 
        // mask to the artifact id to make its value > 0x8000000000000000 (i.e. negative).
        // First, create an index entry for the base artifact.
        HashMap<String, String> solrFields = new HashMap<>();
        String documentId = Long.toString(artifactId);

        solrFields.put(Server.Schema.ID.toString(), documentId);

        // Set the IMAGE_ID field.
        solrFields.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));

        try {
            Ingester.getDefault().indexContentStream(new StringStream(""), solrFields, 0);
        } catch (Ingester.IngesterException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }

        // Next create the index entry for the document content.
        // The content gets added to a single chunk. We may need to add chunking
        // support later.
        long chunkId = 1;

        documentId += "_" + Long.toString(chunkId);
        solrFields.replace(Server.Schema.ID.toString(), documentId);

        StringStream contentStream = new StringStream(artifactContents.toString());

        try {
            Ingester.getDefault().indexContentStream(contentStream, solrFields, contentStream.getSize());
        } catch (Ingester.IngesterException ex) {
            throw new TskCoreException(ex.getCause().getMessage(), ex);
        }
    }

    /**
     * Checks if we can communicate with Solr using the passed-in host and port.
     * Closes the connection upon exit. Throws if it cannot communicate with
     * Solr.
     *
     * When issues occur, it attempts to diagnose them by looking at the
     * exception messages, returning the appropriate user-facing text for the
     * exception received. This method expects the Exceptions messages to be in
     * English and compares against English text.
     *
     * @param host the remote hostname or IP address of the Solr server
     * @param port the remote port for Solr
     *
     * @throws
     * org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException
     *
     */
    @Override
    public void tryConnect(String host, int port) throws KeywordSearchServiceException {
        HttpSolrServer solrServer = null;
        if (host == null || host.isEmpty()) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
        }
        try {
            solrServer = new HttpSolrServer("http://" + host + ":" + Integer.toString(port) + "/solr"); //NON-NLS;
            KeywordSearch.getServer().connectToSolrServer(solrServer);
        } catch (SolrServerException ex) {
            throw new KeywordSearchServiceException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort")); //NON-NLS
        } catch (IOException ex) {
            String result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
            String message = ex.getCause().getMessage().toLowerCase();
            if (message.startsWith(SERVER_REFUSED_CONNECTION)) {
                try {
                    if (InetAddress.getByName(host).isReachable(IS_REACHABLE_TIMEOUT_MS)) {
                        // if we can reach the host, then it's probably port problem
                        result = Bundle.SolrConnectionCheck_Port();
                    } else {
                        result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
                    }
                } catch (IOException | MissingResourceException any) {
                    // it may be anything
                    result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
                }
            } else if (message.startsWith(BAD_IP_ADDRESS_FORMAT)) {
                result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.Hostname"); //NON-NLS
            }
            throw new KeywordSearchServiceException(result);
        } catch (NumberFormatException ex) {
            throw new KeywordSearchServiceException(Bundle.SolrConnectionCheck_Port());
        } catch (IllegalArgumentException ex) {
            throw new KeywordSearchServiceException(ex.getMessage());
        } finally {
            if (null != solrServer) {
                solrServer.shutdown();
            }
        }
    }

    @Override
    public void close() throws IOException {
    }
}
