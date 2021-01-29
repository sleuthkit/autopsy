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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.Host;

/**
 * The data for a host and data sources grouped in this host.
 */
public class HostGrouping implements AutopsyVisitableItem, Comparator<HostGrouping> {

    private final Host host;
    private final Set<DataSourceGrouping> dataSources;

    /**
     * Main constructor.
     *
     * @param host The host.
     * @param dataSources The data sources to be displayed under this host.
     */
    HostGrouping(Host host, Set<DataSourceGrouping> dataSources) {
        this.host = host;
        this.dataSources = (dataSources == null) ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<DataSourceGrouping>(dataSources));
    }

    /**
     * @return The data sources to be displayed under this host.
     */
    Set<DataSourceGrouping> getDataSources() {
        return this.dataSources;
    }

    /**
     * @return The pertinent host object.
     */
    Host getHost() {
        return host;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /* Compares two host groupings to be displayed in a list of children under
     * the person.
     */
    @Override
    public int compare(HostGrouping a, HostGrouping b) {
        String hostA = a == null || a.getHost() == null ? null : a.getHost().getName();
        String hostB = b == null || b.getHost() == null ? null : b.getHost().getName();

        // push unknown host to bottom
        if (hostA == null && hostB == null) {
            return 0;
        } else if (hostA == null) {
            return 1;
        } else if (hostB == null) {
            return -1;
        }

        return hostA.compareToIgnoreCase(hostB);
    }

}
