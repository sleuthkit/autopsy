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

import java.awt.event.ActionListener;


/**
 * common methods for the KeywordSearch TCs / tabs
 * 
 */
public interface KeywordSearchTopComponentInterface {
    
    boolean isMultiwordQuery();
    boolean isLuceneQuerySelected();
    boolean isRegexQuerySelected();
    String getQueryText();
    void setFilesIndexed(int filesIndexed);
    void addSearchButtonListener(ActionListener l);
    
}
