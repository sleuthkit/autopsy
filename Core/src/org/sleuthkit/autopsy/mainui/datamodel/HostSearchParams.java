/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Objects;
import org.sleuthkit.datamodel.Host;

/**
 * Search parameters for a given host.
 */
public class HostSearchParams {
    private static final String TYPE_ID = "Host";
    
    public static String getTypeId() {
        return TYPE_ID;
    }
    
    private final Host host;

    /**
     * Main constructor.
     * @param host The host.
     */
    public HostSearchParams(Host host) {
        this.host = host;
    }

    /**
     * @return The host.
     */
    public Host getHost() {
        return host;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.host);
        return hash;
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
        final HostSearchParams other = (HostSearchParams) obj;
        if (!Objects.equals(this.host, other.host)) {
            return false;
        }
        return true;
    }
    
    
    
}
