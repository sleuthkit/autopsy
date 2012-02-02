/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

//service provider registered in layer.xml
public final class KeywordSearchIngestService implements IngestServiceFsContent {

    private static final Logger logger = Logger.getLogger(KeywordSearchIngestService.class.getName());
    private static KeywordSearchIngestService instance = null;
    private IngestManager manager;
    private int messageID = 0;
    private static final long MAX_STRING_EXTRACT_SIZE = 10 * (1 << 10) * (1 << 10);
    private static final long MAX_INDEX_SIZE = 200 * (1 << 10) * (1 << 10);
    private Ingester ingester;

    public enum IngestStatus {
        INGESTED, EXTRACTED_INGESTED, SKIPPED_EXTRACTION,};
    private Map<Long, IngestStatus> ingestStatus;

    public static synchronized KeywordSearchIngestService getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestService();
        }
        return instance;
    }

    @Override
    public void process(FsContent fsContent) {
        final long size = fsContent.getSize();
        //logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
        if (! fsContent.isFile() )
            return;
        
        if (size == 0 || size > MAX_INDEX_SIZE) {
            ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED_EXTRACTION);
            return;
        }
        
        try {
            logger.log(Level.INFO, "indexing: " + fsContent.getName());
            ingester.ingest(fsContent);
            ingestStatus.put(fsContent.getId(), IngestStatus.INGESTED);
        } catch (IngesterException e) {
            ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED_EXTRACTION);
            //try to extract strings
            if (fsContent.getSize() < MAX_STRING_EXTRACT_SIZE) {
                if (!extractAndIngest(fsContent)) {
                    logger.log(Level.INFO, "Failed to extract strings and ingest, file '" + fsContent.getName() + "' (id: " + fsContent.getId() + ").");
                } else {
                    ingestStatus.put(fsContent.getId(), IngestStatus.EXTRACTED_INGESTED);
                }
            } else {
                ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED_EXTRACTION);
            }

        }
       

    }

    @Override
    public void complete() {
        //logger.log(Level.INFO, "complete()");
        ingester.commit();

        //signal a potential change in number of indexed files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().getCore().queryNumIndexedFiles();
            KeywordSearch.changeSupport.firePropertyChange(KeywordSearch.NUM_FILES_CHANGE_EVT, null, new Integer(numIndexedFiles));
        } catch (SolrServerException se) {
            logger.log(Level.INFO, "Error executing Solr query to check number of indexed files: ", se);
        }

        manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Complete"));
        //manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Indexed files: " + ingestStat));
        //postSummary();
    }

    @Override
    public String getName() {
        return "Keyword Search";
    }

    @Override
    public void init(IngestManager manager) {
        logger.log(Level.INFO, "init()");
        this.manager = manager;
        
        final Server.Core solrCore = KeywordSearch.getServer().getCore();
        ingester = solrCore.getIngester();

        ingestStatus = new HashMap<Long, IngestStatus>();

        manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Started"));
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
        ingester.commit();
        //signal a potential change in number of indexed files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().getCore().queryNumIndexedFiles();
            KeywordSearch.changeSupport.firePropertyChange(KeywordSearch.NUM_FILES_CHANGE_EVT, null, new Integer(numIndexedFiles));
        } catch (SolrServerException se) {
            logger.log(Level.INFO, "Error executing Solr query to check number of indexed files: ", se);
        }
        //postSummary();
    }

    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }

    private boolean extractAndIngest(FsContent f) {
        boolean success = false;
        FsContentStringStream fscs = new FsContentStringStream(f, FsContentStringStream.Encoding.ASCII);
        try {
            fscs.convert();
            ingester.ingest(fscs);
            success = true;
        } catch (TskException tskEx) {
            logger.log(Level.INFO, "Problem extracting string from file: '" + f.getName() + "' (id: " + f.getId() + ").", tskEx);
        } catch (IngesterException ingEx) {
            logger.log(Level.INFO, "Ingester had a problem with extracted strings from file '" + f.getName() + "' (id: " + f.getId() + ").", ingEx);
        }
        return success;
    }

    private void postSummary() {
        int indexed = 0;
        int indexed_extr = 0;
        int skipped = 0;
        for (IngestStatus s : ingestStatus.values()) {
            switch (s){
                case INGESTED:
                    ++indexed;
                    break;
                case EXTRACTED_INGESTED:
                    ++indexed_extr;
                    break;
                case SKIPPED_EXTRACTION:
                    ++skipped;
                    break;
                default:
                    ;
            }
        }
        manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Indexed files: " + indexed));
        manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Indexed strings: " + indexed_extr));
        manager.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Skipped files: " + skipped));     
    }
}
