/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;

/**
 * View the content associated with the given BlackboardArtifactNode
 */
class ViewAssociatedContentAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(ViewAssociatedContentAction.class.getName());
    private final Content content;

    public ViewAssociatedContentAction(String title, BlackboardArtifactNode node) {
        super(title);
        this.content = node.getLookup().lookup(Content.class);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Node node = RootContentChildren.createNode(this.content);
        if (node == null) {
            logger.log(Level.WARNING, "No node created for object: " + this.content);
        } else {
            DataContentTopComponent.getDefault().setNode(node);
        }
    }
}
