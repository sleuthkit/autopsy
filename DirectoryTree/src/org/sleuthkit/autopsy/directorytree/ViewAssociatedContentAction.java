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
import javax.swing.AbstractAction;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;

/**
 * View the content associated with the given BlackboardArtifactNode
 */
class ViewAssociatedContentAction extends AbstractAction {

    private BlackboardArtifactNode node;

    public ViewAssociatedContentAction(String title, Node node) {
        super(title);
        this.node = (BlackboardArtifactNode) node;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataContentTopComponent.getDefault().setNode(node.getContentNode());
    }
}
