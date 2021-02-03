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

import com.google.common.eventbus.Subscribe;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.Host;

/**
 * A node to be displayed in the UI tree for a person and persons grouped in
 * this host.
 */
@NbBundle.Messages(value = {"PersonNode_unknownHostNode_title=Unknown Persons"})
class PersonGroupingNode extends DisplayableItemNode {
    
    // stub class until this goes into TSK datamodel.
    static class Person {

        private final String name;
        private final long id;

        public Person(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getName() {
            return name;
        }
        
        public long getId() {
            return id;
        }
    }
    
    
    private static class PersonChildren extends ChildFactory<Host> {

        private final Set<Host> hosts = new HashSet<>();

        @Override
        protected boolean createKeys(List<Host> toPopulate) {
            toPopulate.addAll(hosts);
            return true;
        }

        @Override
        protected HostGroupingNode createNodeForKey(Host key) {
            return key == null ? null : new HostGroupingNode(key);
        }

        private void refresh(Set<Host> newHosts) {
            hosts.clear();
            if (newHosts != null) {
                hosts.addAll(newHosts);
            }
            super.refresh(true);
        }
    }

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/person.png";

    private final Person person;
    private final PersonChildren personChildren;
    
    /**
     * Filters and sorts data source groupings to be displayed in the tree.
     *
     * @param dataSources The data source grouping data.
     * @return The data source groupings to be displayed.
     */
//    private static List<HostGrouping> getSortedFiltered(Collection<HostGrouping> hosts) {
//        return (hosts == null) ? Collections.emptyList()
//                : hosts.stream()
//                        .filter(p -> p != null)
//                        .sorted()
//                        .collect(Collectors.toList());
//    }
    
    
    PersonGroupingNode(Person person) {
        this(person, new PersonChildren());
    }
    
    private PersonGroupingNode(Person person, PersonChildren personChildren) {
        super(Children.create(personChildren, false), person == null ? null : Lookups.singleton(person));

        String safeName = (person == null || person.getName() == null)
                ? Bundle.HostNode_unknownHostNode_title()
                : person.getName();

        super.setName(safeName);
        super.setDisplayName(safeName);
        this.setIconBaseWithExtension(ICON_PATH);
        this.person = person;
        this.personChildren = personChildren;
    }
    
    @Subscribe
    void update(Map<Long, Set<Host>> personHostMapping) {
        Long id = this.person == null ? null : person.getId();
        Set<Host> hosts = personHostMapping == null ? null : personHostMapping.get(id);
        this.personChildren.refresh(hosts);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
