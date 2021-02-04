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
import org.sleuthkit.datamodel.HostManager;

/**
 * A top level UI grouping of data sources under a host.
 */
public class HostGrouping implements AutopsyVisitableItem {

    private final Host host;
    private final HostManager hostManager;

    /**
     * Main constructor.
     *
     * @param hostManager The host manager from which to gather information
     * about the host.
     * @param host The host record.
     */
    HostGrouping(HostManager hostManager, Host host) {
        this.host = host;
        this.hostManager = hostManager;
    }

    /**
     * @return The host manager from which to gather information about the host.
     */
    HostManager getHostManager() {
        return hostManager;
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
        return Objects.hashCode(this.host == null ? 0 : this.host.getId());
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
        long thisId = (this.getHost() == null) ? 0 : this.getHost().getId();
        long otherId = (other.getHost() == null) ? 0 : other.getHost().getId();
        return thisId == otherId;
    }

}
