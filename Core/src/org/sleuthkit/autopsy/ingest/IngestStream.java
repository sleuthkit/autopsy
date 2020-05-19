/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;


/**
 *
 */
public interface IngestStream {
    void addDataSource(long dataSourceObjectId) throws IngestStreamClosedException;

    DataSource getDataSource() throws TskCoreException;
    
    void addFiles(List<Long> fileObjectIds) throws IngestStreamClosedException;
    
    List<AbstractFile> getNextFiles(int numberOfFiles) throws TskCoreException;

    void close(boolean completed);
    
    boolean isClosed();
}
