/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.apache.solr.common.util.ContentStreamBase.StringStream;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.openide.util.NbBundle;
import java.net.InetAddress;
import java.util.MissingResourceException;

/**
 * An implementation of the KeywordSearchService interface that uses Solr for
 * text indexing and search.
 */
@ServiceProvider(service = KeywordSearchService.class)
public class SolrSearchService implements KeywordSearchService {

    private static final String BAD_IP_ADDRESS_FORMAT = "ioexception occured when talking to server";
    private static final String SERVER_REFUSED_CONNECTION = "server refused connection";
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
            artifactContents.append(attribute.getAttributeTypeDisplayName());
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
            if (attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()
                    || attribute.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()) {

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
            Ingester.getDefault().ingest(new StringStream(""), solrFields, 0);
        } catch (Ingester.IngesterException ex) {
        }

        // Next create the index entry for the document content.
        // The content gets added to a single chunk. We may need to add chunking
        // support later.
        long chunkId = 1;

        documentId += "_" + Long.toString(chunkId);
        solrFields.replace(Server.Schema.ID.toString(), documentId);

        StringStream contentStream = new StringStream(artifactContents.toString());

        try {
            Ingester.getDefault().ingest(contentStream, solrFields, contentStream.getSize());
        } catch (Ingester.IngesterException ex) {
        }
    }

    /**
     * Checks if we can communicate with Solr using the passed-in host and port.
     * Closes the connection upon exit. Throws if it cannot communicate with
     * Solr.
     *
     * @param host the remote hostname or IP address of the Solr server
     * @param port the remote port for Solr
     *
     * @throws java.io.IOException
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    @Override
    public void tryConnect(String host, String port) throws NumberFormatException, IOException, TskCoreException {
        try {
            if (host == null || host.isEmpty()) {
                throw new TskCoreException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingHostname")); //NON-NLS
            } else if (port == null || port.isEmpty()) {
                throw new TskCoreException(NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.MissingPort")); //NON-NLS
            }
            // if the port value is invalid, throw
            Integer.parseInt(port);
            HttpSolrServer solrServer = new HttpSolrServer("http://" + host + ":" + port + "/solr"); //NON-NLS;
            KeywordSearch.getServer().connectToSolrServer(solrServer);
            if (null != solrServer) {
                solrServer.shutdown();
            }
        } catch (SolrServerException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * This method handles exceptions from the connection tester, tryConnect(),
     * returning the appropriate user-facing text for the exception received.
     *
     * @param ex        the exception that was returned
     * @param ipAddress the IP address to connect to
     *
     * @return returns the String message to show the user
     */
    @Override
    public String getUserWarning(Exception ex, String ipAddress) {

        String result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
        if (ex instanceof IOException) {
            String message = ex.getCause().getMessage().toLowerCase();
            if (message.startsWith(SERVER_REFUSED_CONNECTION)) {
                try {
                    if (InetAddress.getByName(ipAddress).isReachable(IS_REACHABLE_TIMEOUT_MS)) {
                        // if we can reach the host, then it's probably port problem
                        result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.Port"); //NON-NLS
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
        } else if (ex instanceof SolrServerException) {
            result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.HostnameOrPort"); //NON-NLS
        } else if (ex instanceof NumberFormatException) {
            result = NbBundle.getMessage(SolrSearchService.class, "SolrConnectionCheck.Port"); //NON-NLS
        } else if (ex instanceof TskCoreException) {
            result = ex.getMessage();
        } else {
            result = ex.getMessage();
        }
        return result;
    }

    @Override
    public void close() throws IOException {
    }
}
