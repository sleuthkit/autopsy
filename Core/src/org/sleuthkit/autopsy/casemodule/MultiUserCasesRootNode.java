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
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coordinationservice.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A root node for displaying MultiUserCaseNodes in a NetBeans Explorer View.
 */
final class MultiUserCasesRootNode extends AbstractNode {

    /**
     * Constructs a root node for displaying MultiUserCaseNodes in a NetBeans
     * Explorer View.
     *
     * @param case A list of coordination service node data objects representing
     *             multi-user cases.
     */
    MultiUserCasesRootNode(List<CaseNodeData> cases) {
        super(Children.create(new MultiUserCasesRootNodeChildren(cases), true));
    }

    private static class MultiUserCasesRootNodeChildren extends ChildFactory<CaseNodeData> {

        private final List<CaseNodeData> cases;

        MultiUserCasesRootNodeChildren(List<CaseNodeData> cases) {
            this.cases = cases;
        }

        @Override
        protected boolean createKeys(List<CaseNodeData> keys) {
            if (cases != null && cases.size() > 0) {
                keys.addAll(cases);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(CaseNodeData key) {
            return new MultiUserCaseNode(key);
        }

    }

}
