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

import java.util.Objects;
import org.sleuthkit.datamodel.Host;

/**
 * A top level UI grouping of data sources under a host.
 */
public class HostGrouping implements AutopsyVisitableItem, Comparable<HostGrouping> {

    private final Host host;

    /**
     * Main constructor.
     *
     * @param host The host record.
     */
    HostGrouping(Host host) {
        this.host = host;
    }

    /**
     * @return The pertinent host.
     */
    Host getHost() {
        return host;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.host == null ? 0 : this.host.getHostId());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HostGrouping other = (HostGrouping) obj;
        long thisId = (this.getHost() == null) ? 0 : this.getHost().getHostId();
        long otherId = (other.getHost() == null) ? 0 : other.getHost().getHostId();
        return thisId == otherId;
    }

    /* 
     * Compares two host groupings to be displayed in a list of children under
     * the person.
     */
    @Override
    public int compareTo(HostGrouping o) {
        String thisHost = this.getHost() == null ? null : this.getHost().getName();
        String otherHost = o == null || o.getHost() == null ? null : o.getHost().getName();

        // push unknown host to bottom
        if (thisHost == null && otherHost == null) {
            return 0;
        } else if (thisHost == null) {
            return 1;
        } else if (otherHost == null) {
            return -1;
        }

        return thisHost.compareToIgnoreCase(otherHost);
    }

}
