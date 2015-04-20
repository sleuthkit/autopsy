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
package org.sleuthkit.autopsy.ingest.events;

import java.io.Serializable;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Event published when the analysis (ingest) of a file is completed. The "old"
 * value is the Autopsy object id of the file. The "new" value is an
 * AbstractFile object for that id.
 */
public final class FileAnalyzedEvent extends AutopsyEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ContentChangedEvent.class.getName());
    private transient AbstractFile file;

    /**
     * Constructs an event that can be used to indicate that the analysis
     * (ingest) of a file is completed.
     *
     * @param sourceType Local or remote event source.
     * @param file The file for which analysis is completed.
     */
    public FileAnalyzedEvent(AutopsyEvent.SourceType sourceType, AbstractFile file) {
        /**
         * Putting null into newValue to allow for lazy loading of the
         * AbstractFile object. This bypasses the issues related to the
         * serialization and de-serialization of AbstractFile objects for remote
         * events.
         */
        super(sourceType, IngestManager.IngestModuleEvent.FILE_DONE.toString(), file.getId(), null);
    }

    /**
     * Gets the file for which analysis is completed.
     *
     * @return The file.
     */
    @Override
    public Object getNewValue() {
        /**
         * The file field is set in the constructor, but it is transient so it
         * will become null for a remote event. Note that doing a lazy load of
         * the AbstractFile object bypasses the issues related to the
         * serialization and de-serialization of AbstractFile objects for remote
         * events. It also may save database round trips from receiving hosts.
         */
        if (null != file) {
            return file;
        }
        try {
            long id = (Long) super.getNewValue();
            file = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(id);
            return file;
        } catch (IllegalStateException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Error doing lazy load for remote event", ex);
            return null;
        }
    }

}
