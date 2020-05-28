/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule;

import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestStream;
import org.sleuthkit.autopsy.ingest.IngestStreamClosedException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This is a default ingest stream to use with the data source processors when an IngestStream is not supplied.
 * Adding files/data sources are no-ops but errors will be thrown if there is an attempt
 * to access them through this class.
 */
class DefaultIngestStream implements IngestStream {

    private volatile boolean isClosed = false;

    @Override
    public void addDataSource(long dataSourceObjectId) throws IngestStreamClosedException {
        // Do nothing
        System.out.println("### DefaultAddImageTaskIngestStream - addDataSource " + dataSourceObjectId);
    }

    @Override
    public DataSource getDataSource() throws TskCoreException {
        throw new UnsupportedOperationException("Can not get data source from DefaultAddImageTaskIngestStream");
    }

    @Override
    public void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException {
        // Do nothing
        System.out.println("### DefaultAddImageTaskIngestStream - addFiles " + fileObjectIds.size());
    }

    @Override
    public List<AbstractFile> getNextFiles(int numberOfFiles) throws TskCoreException {
        throw new UnsupportedOperationException("Can not get files from DefaultAddImageTaskIngestStream");
    }

    @Override
    public void close(boolean completed) {
        isClosed = true;
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }
}
