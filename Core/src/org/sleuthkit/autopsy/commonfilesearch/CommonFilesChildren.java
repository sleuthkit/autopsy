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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.sleuthkit.datamodel.AbstractFile;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.CommonFileParentNode;

/**
 * Makes nodes for common files search results.
 */
public final class CommonFilesChildren extends ChildFactory<CommonFilesMetaData> {

    private List<CommonFilesMetaData> metaDataList;

    public CommonFilesChildren(List<CommonFilesMetaData> theMetaDataList) {
        super();
        this.metaDataList = theMetaDataList;
    }

    protected void removeNotify() {
        metaDataList = null;
    }

    @Override
    protected Node createNodeForKey(CommonFilesMetaData metaData) {

        return new CommonFileParentNode(metaData);
    }

    @Override
    protected boolean createKeys(List<CommonFilesMetaData> toPopulate) {
        toPopulate.addAll(metaDataList);
        return true;
    }


}
