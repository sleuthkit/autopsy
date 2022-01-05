/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.sleuthkit.autopsy.filesearch.FileSearchAction;

/**
 * The “File Search by Attributes” action for data sources in the tree.
 */
public class FileSearchTreeAction extends AbstractAction {

    private final long dataSourceId;
    private FileSearchAction searcher; 

    /**
     * Main constructor.
     *
     * @param title        The display name for the action.
     * @param dataSourceID The data source id of the item that is selected in
     *                     the tree.
     */
    public FileSearchTreeAction(String title, long dataSourceID) {
        super(title);
        dataSourceId = dataSourceID;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(searcher == null) {
            searcher = FileSearchAction.getDefault();
        }
        
        searcher.showDialog(dataSourceId);
    }

}
