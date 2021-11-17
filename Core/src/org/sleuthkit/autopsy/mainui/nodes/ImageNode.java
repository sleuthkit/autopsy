/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.directorytree.ExtractUnallocAction;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemRowDTO.ImageRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.datamodel.Content;

/**
 * A node representing an Image.
 */
public class ImageNode extends BaseNode<SearchResultsDTO, ImageRowDTO> {

    /**
     * Simple node constructor.
     *
     * @param results The search result DTO.
     * @param row     The table row DTO.
     */
    public ImageNode(SearchResultsDTO results, ImageRowDTO row) {
        super(Children.LEAF, ContentNodeUtil.getLookup(row.getContent()), results, row);
        setDisplayName(row.getContent().getName());
        setShortDescription(row.getContent().getName());
        setIconBaseWithExtension(NodeIconUtil.IMAGE.getPath()); //NON-NLS
    }

    @NbBundle.Messages({
        "ImageNode_ExtractUnallocAction_text=Extract Unallocated Space to Single Files"
    })

    @Override
    public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
        ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
        group.add(new ExtractUnallocAction(
                Bundle.ImageNode_ExtractUnallocAction_text(), getRowDTO().getContent()));
        return Optional.of(group);
    }

    @Override
    public Optional<Content> getDataSourceForActions() {
        return Optional.of(getRowDTO().getContent());
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }
}
