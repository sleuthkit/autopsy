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

import java.util.List;
import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.directorytree.ExtractUnallocAction;
import org.sleuthkit.autopsy.directorytree.FileSystemDetailsAction;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.VolumeRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory.ActionGroup;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.datamodel.Content;

/**
 * A node representing a Volume.
 */
public class VolumeNode extends BaseNode<SearchResultsDTO, VolumeRowDTO> implements SCOSupporter {

    /**
     * Simple node constructor.
     *
     * @param results The search result DTO.
     * @param row     The table row DTO.
     */
    public VolumeNode(SearchResultsDTO results, VolumeRowDTO row) {
        super(Children.LEAF, ContentNodeUtil.getLookup(row.getContent()), results, row);
        setIconBaseWithExtension(NodeIconUtil.VOLUME.getPath()); //NON-NLS

        // use first cell value for display name
        String displayName = row.getCellValues().size() > 0
                ? row.getCellValues().get(0).toString()
                : "";

        setDisplayName(displayName);
        setShortDescription(displayName);
    }

    @Messages({
        "VolumnNode_ExtractUnallocAction_text=Extract Unallocated Space to Single Files"
    })

    @Override
    public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
        ActionGroup group = new ActionGroup();
        group.add(new ExtractUnallocAction(
                Bundle.VolumnNode_ExtractUnallocAction_text(), getRowDTO().getContent()));
        group.add(new FileSystemDetailsAction(getRowDTO().getContent()));
        return Optional.of(group);
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }

    @Override
    public Optional<Content> getContent() {
        return Optional.ofNullable(getRowDTO().getContent());
    }

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }
}
