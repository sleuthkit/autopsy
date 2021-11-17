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

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.PoolRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.BaseNode.backgroundTasksPool;
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.datamodel.Content;

/**
 * A node representing a Pool.
 */
public class PoolNode extends BaseNode<SearchResultsDTO, PoolRowDTO> implements SCOSupporter {

    /**
     * Pool node constructor.
     *
     * @param results Search Result DTO.
     * @param row     Pool table row DTO.
     */
    public PoolNode(SearchResultsDTO results, PoolRowDTO row) {
        super(Children.LEAF,
                Lookups.fixed(row.getContent(), new TskContentItem<>(row.getContent())),
                results, row);

        String name = row.getContent().getType().getName();
        setDisplayName(name);
        setShortDescription(name);
        setIconBaseWithExtension("org/sleuthkit/autopsy/images/pool-icon.png");
    }

    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        backgroundTasksPool.submit(new SCOFetcher<>(new WeakReference<>(this)));
        return sheet;
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
