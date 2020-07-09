/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
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
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * A collection of case-level services: file manager, tags manager, keyword
 * search service, artifacts blackboard.
 * 
 * TODO (AUT-2158): This class should not extend Closeable.
 */
public class Services implements Closeable {

    private final FileManager fileManager;
    private final TagsManager tagsManager;

    /**
     * Constructs a collection of case-level services: file manager, tags
     * manager, keyword search service, artifacts blackboard.
     *
     * @param caseDb The case database for the current case.
     */
    public Services(SleuthkitCase caseDb) {
        fileManager = new FileManager(caseDb);
        tagsManager = new TagsManager(caseDb);
    }

    /**
     * Gets the file manager for the current case.
     *
     * @return The file manager service for the current case.
     */
    public FileManager getFileManager() {
        return fileManager;
    }

    /**
     * Gets the tags manager for the current case.
     *
     * @return The tags manager service for the current case.
     */
    public TagsManager getTagsManager() {
        return tagsManager;
    }

    /**
     * Gets the keyword search service for the current case.
     *
     * @return The keyword search service for the current case.
     */
    public KeywordSearchService getKeywordSearchService() {
        return Lookup.getDefault().lookup(KeywordSearchService.class);
    }

    /**
     * Gets the artifacts blackboard for the current case.
     *
     * @return org.sleuthkit.datamodel.Blackboard Blackboard for the current
     *         case.
     */
    public org.sleuthkit.datamodel.Blackboard getArtifactsBlackboard() {
        return Case.getCurrentCase().getSleuthkitCase().getBlackboard();
    }

    /**
     * Gets the artifacts blackboard for the current case.
     *
     * @return The blackboard service for the current case.
     *
     * @deprecated Use org.sleuthkit.autopsy.casemodule.getArtifactsBlackboard
     * instead
     */
    @Deprecated
    public Blackboard getBlackboard() {
        return new Blackboard();
    }

    /**
     * Closes the services for the current case.
     *
     * @throws IOException if there is a problem closing the services. 
    * @deprecated Do not use.
     */
    @Deprecated
    @Override
    public void close() throws IOException {
        /*
         * No-op maintained for backwards compatibility. Clients should not
         * attempt to close case services.
         */
    }

}
