/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A root node for displaying MultiUserCaseNodes in a NetBeans Explorer View.
 */
final class MultiUserCasesRootNode extends AbstractNode {

    private static final Logger logger = Logger.getLogger(MultiUserCasesRootNode.class.getName());    
    
    /**
     * Constructs a root node for displaying MultiUserCaseNodes in a NetBeans
     * Explorer View.
     *
     * @param case A list of coordination service node data objects representing
     *             multi-user cases.
     */
    MultiUserCasesRootNode() {
        super(Children.create(new MultiUserCasesRootNodeChildren(), true));
    }

    private static class MultiUserCasesRootNodeChildren extends ChildFactory<CaseNodeData> {

        @Override
        protected boolean createKeys(List<CaseNodeData> keys) {
            try {
                List<CaseNodeData> caseNodeData = MulitUserCaseNodeDataCollector.getNodeData();
                keys.addAll(caseNodeData);
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, "Failed to get case node data from coodination service", ex);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(CaseNodeData key) {
            return new MultiUserCaseNode(key);
        }

    }

}
