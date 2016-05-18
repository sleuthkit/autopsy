/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2016 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * A class to manage various services.
 */
public class Services implements Closeable {

    private final List<Closeable> services = new ArrayList<>();
    private final FileManager fileManager;
    private final TagsManager tagsManager;
    private final KeywordSearchService keywordSearchService;
    private final Blackboard blackboard;

    Services(Case currentCase, SleuthkitCase caseDb) {
        fileManager = new FileManager(currentCase, caseDb);
        services.add(fileManager);

        tagsManager = new TagsManager(caseDb);
        services.add(tagsManager);

        keywordSearchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        services.add(keywordSearchService);
        
        blackboard = new Blackboard();
        services.add(blackboard);        
    }
    
    public Services(SleuthkitCase tskCase) {
        fileManager = new FileManager(tskCase);
        services.add(fileManager);

        tagsManager = new TagsManager(tskCase);
        services.add(tagsManager);

        keywordSearchService = Lookup.getDefault().lookup(KeywordSearchService.class);
        services.add(keywordSearchService);
        
        blackboard = new Blackboard();
        services.add(blackboard);
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public TagsManager getTagsManager() {
        return tagsManager;
    }

    public KeywordSearchService getKeywordSearchService() {
        return keywordSearchService;
    }
    
    public Blackboard getBlackboard() {
        return blackboard;
    }

    @Override
    public void close() throws IOException {
        for (Closeable service : services) {
            service.close();
        }
    }

}
