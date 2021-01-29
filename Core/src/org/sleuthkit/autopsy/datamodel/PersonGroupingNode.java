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
package org.sleuthkit.autopsy.datamodel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 * A node to be displayed in the UI tree for a person and persons grouped in
 * this host.
 */
@NbBundle.Messages(value = {"PersonNode_unknownHostNode_title=Unknown Persons"})
class PersonGroupingNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/person.png";

    /**
     * Get the host groups filtered and sorted to display in the UI as children
     * under a person.
     *
     * @param hosts The hosts.
     * @return The sorted filtered list.
     */
    private static List<HostGrouping> getSortedFiltered(Collection<HostGrouping> hosts) {
        return (hosts == null) ? Collections.emptyList()
                : hosts.stream()
                        .filter(p -> p != null)
                        .sorted()
                        .collect(Collectors.toList());
    }

    private final boolean isLeaf;

    /**
     * Main constructor.
     *
     * @param personGroup The data for the person to be displayed.
     */
    PersonGroupingNode(PersonGrouping personGroup) {
        this(personGroup, (personGroup != null) ? personGroup.getHosts() : null);
    }

    private PersonGroupingNode(PersonGrouping personGroup, Set<HostGrouping> nodeChildren) {
        super(new RootContentChildren(getSortedFiltered(nodeChildren)), personGroup == null ? null : Lookups.singleton(personGroup));

        String safeName = (personGroup == null || personGroup.getPerson() == null || personGroup.getPerson().getName() == null)
                ? Bundle.PersonNode_unknownHostNode_title()
                : personGroup.getPerson().getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.isLeaf = CollectionUtils.isEmpty(nodeChildren);
    }

    @Override
    public boolean isLeafTypeNode() {
        return isLeaf;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

}
