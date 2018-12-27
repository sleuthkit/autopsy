/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.List;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * A collection of case-level services (e.g., file manager, tags manager,
 * keyword search, blackboard).
 */
public class Services implements Closeable {

    private final List<Closeable> servicesList = new ArrayList<>();
    private final FileManager fileManager;
    private final TagsManager tagsManager;
    private final KeywordSearchService keywordSearchService;
    private final Blackboard blackboard;

    /**
     * Constructs a collection of case-level services (e.g., file manager, tags
     * manager, keyword search, blackboard).
     *
     * @param caseDb The case database for the current case.
     */
    public Services(SleuthkitCase caseDb) {
        fileManager = new FileManager(caseDb);
        servicesList.add(fileManager);

        tagsManager = new TagsManager(caseDb);
        servicesList.add(tagsManager);

        //This lookup fails in the functional test code. See JIRA-4571 for details.
        //For the time being, the closing of this service at line 108 will be made
        //null safe so that the functional tests run with no issues.
        keywordSearchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        servicesList.add(keywordSearchService);

        blackboard = new Blackboard(caseDb);
        servicesList.add(blackboard);
    }

    /**
     * Gets the file manager service for the current case.
     *
     * @return The file manager service for the current case.
     */
    public FileManager getFileManager() {
        return fileManager;
    }

    /**
     * Gets the tags manager service for the current case.
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
        return keywordSearchService;
    }

    /**
     * Gets the blackboard service for the current case.
     *
     * @return The blackboard service for the current case.
     *
     * @deprecated Use SleuthkitCase.getBlackboard() instead.
     */
    @Deprecated
    public Blackboard getBlackboard() {
        return blackboard;
    }

    /**
     * Closes the services for the current case.
     *
     * @throws IOException if there is a problem closing the services.
     */
    @Override
    public void close() throws IOException {
        for (Closeable service : servicesList) {
            if(service != null) {
                 service.close();
             }
        }
    }

}
