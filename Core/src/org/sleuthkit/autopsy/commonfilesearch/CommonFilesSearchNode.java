/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.util.ArrayList;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Encapsulates data used to display common files search results in the top
 * right pane.
 */
//TODO rename to CommonFilesSearchNode
final class CommonFilesSearchNode extends AbstractNode {

    private CommonFilesChildren children;

    CommonFilesSearchNode(List<AbstractFile> keys, java.util.Map<String, Integer> instanceCountMap, java.util.Map<String, String> dataSourceMap) {
        super(new CommonFilesChildren(true, keys, instanceCountMap, dataSourceMap));
        this.children = (CommonFilesChildren) this.getChildren();
    }

    @NbBundle.Messages({
        "CommonFilesNode.getName.text=CommonFiles"})
    @Override
    public String getName() {
        return Bundle.CommonFilesNode_getName_text();
    }
}
