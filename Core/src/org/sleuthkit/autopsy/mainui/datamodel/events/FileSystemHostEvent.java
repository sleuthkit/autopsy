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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;

/**
 * An event signaling that a data source has been added or removed from the
 * given Host.
 */
public class FileSystemHostEvent implements DAOEvent {

    private final Host host;
    private final Content dataSource;

    public FileSystemHostEvent(Host host, Content dataSource) {
        this.host = host;
        this.dataSource = dataSource;
    }

    public Host getHost() {
        return host;
    }

    public Content getDataSource() {
        return dataSource;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + Objects.hashCode(this.host);
        hash = 59 * hash + Objects.hashCode(this.dataSource);
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
        final FileSystemHostEvent other = (FileSystemHostEvent) obj;
        if (!Objects.equals(this.host, other.host)) {
            return false;
        }
        if (!Objects.equals(this.dataSource, other.dataSource)) {
            return false;
        }
        return true;
    }

    
    
    @Override
    public DAOEvent.Type getType() {
        return DAOEvent.Type.RESULT;
    }
}
