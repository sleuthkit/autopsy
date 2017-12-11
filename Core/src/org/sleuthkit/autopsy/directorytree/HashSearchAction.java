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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import javax.annotation.concurrent.Immutable;
import javax.swing.AbstractAction;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbSearchAction;

/**
 * Action to lookup the interface and call the real action in HashDatabase. The
 * real action, HashDbSearchAction, implements HashSearchProvider, and should be
 * the only instance of it.
 *
 * //TODO: HashDBSearchAction needs a public constructor and a service
 * registration annotation for the lookup technique to work
 */
@Immutable
public class HashSearchAction extends AbstractAction {

    private final Node contentNode;

    public HashSearchAction(String title, Node contentNode) {
        super(title);
        this.contentNode = contentNode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //HashSearchProvider searcher = Lookup.getDefault().lookup(HashSearchProvider.class);
        //TODO: HashDBSearchAction needs a public constructor and a service registration annotation for the above technique to work
        HashDbSearchAction searcher = HashDbSearchAction.getDefault();
        searcher.search(contentNode);
    }
}
