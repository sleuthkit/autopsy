/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Interface/Node manager for hash searching. The manager takes in the raw map
 * of MD5 hashes to files, flattens the map, and sends it to the
 * HashDbSearchResultFactory.
 */
class HashDbSearchManager {

    private Map<String, List<AbstractFile>> map;
    private List<AbstractFile> kvContents;

    public HashDbSearchManager(Map<String, List<AbstractFile>> map) {
        this.map = map;
        init();
    }

    /**
     * Initializes the flattened map of KeyValues. Each map in a KeyValue is a
     * row in the table, with the String as it's column name and the Object as
     * it's value in the row.
     */
    private void init() {
        if (!map.isEmpty()) {
            kvContents = new ArrayList<AbstractFile>();
            int id = 0;
            for (String s : map.keySet()) {
                for (AbstractFile file : map.get(s)) {
                    kvContents.add(file);
                }
            }
        }
    }

    /**
     * Takes the key values, creates nodes through the
     * HashDbSearchResultFactory, and displays it in the TopComponet.
     */
    public void execute() {
        if (!map.isEmpty()) {
            Collection<AbstractFile> kvCollection = kvContents;
            Node rootNode = null;

            if (kvCollection.size() > 0) {
                Children childKeyValueContentNodes
                        = Children.create(new HashDbSearchResultFactory(kvCollection), true);

                rootNode = new AbstractNode(childKeyValueContentNodes);
            } else {
                rootNode = Node.EMPTY;
            }

            final String pathText = NbBundle.getMessage(this.getClass(), "HashDbSearchManager.MD5HashSearch");
            TopComponent searchResultWin = DataResultTopComponent.createInstance(
                    NbBundle.getMessage(this.getClass(), "HashDbSearchManager.MD5HashSearch"),
                    pathText,
                    rootNode,
                    kvCollection.size());

            searchResultWin.requestActive();
        } else {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    NbBundle.getMessage(this.getClass(), "HashDbSearchManager.noResultsFoundMsg"));
        }
    }
}
