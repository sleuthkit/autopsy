/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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

import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.DataSourcesNode.DataSourcesNodeChildren;

/**
 * Device node in the DataSource tree
 *
 */
final class DeviceNode extends DisplayableItemNode {

    DeviceNode(String deviceID) {
        super(new DataSourcesNodeChildren(deviceID), Lookups.singleton(deviceID));
        setName(deviceID);

        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/device-16.png");   //NON-NLS
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
