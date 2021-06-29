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

import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.LocalFilesDataSource;

/**
 *
 *
 */
public class LocalFilesDataSourceNode extends VirtualDirectoryNode {

    private final LocalFilesDataSource localFileDataSource;

    public LocalFilesDataSourceNode(LocalFilesDataSource ld) {
        super(ld);
        localFileDataSource = ld;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png"); //NON-NLS
    }

    @Override
    @NbBundle.Messages({"LocalFilesDataSourceNode.createSheet.size.name=Size (Bytes)",
        "LocalFilesDataSourceNode.createSheet.size.displayName=Size (Bytes)",
        "LocalFilesDataSourceNode.createSheet.size.desc=Size of the data source in bytes.",
        "LocalFilesDataSourceNode.createSheet.type.name=Type",
        "LocalFilesDataSourceNode.createSheet.type.displayName=Type",
        "LocalFilesDataSourceNode.createSheet.type.desc=Type of the image.",
        "LocalFilesDataSourceNode.createSheet.type.text=Logical File Set",
        "LocalFilesDataSourceNode.createSheet.timezone.name=Timezone",
        "LocalFilesDataSourceNode.createSheet.timezone.displayName=Timezone",
        "LocalFilesDataSourceNode.createSheet.timezone.desc=Timezone of the image",
        "LocalFilesDataSourceNode.createSheet.deviceId.name=Device ID",
        "LocalFilesDataSourceNode.createSheet.deviceId.displayName=Device ID",
        "LocalFilesDataSourceNode.createSheet.deviceId.desc=Device ID of the image",
        "LocalFilesDataSourceNode.createSheet.name.name=Name",
        "LocalFilesDataSourceNode.createSheet.name.displayName=Name",
        "LocalFilesDataSourceNode.createSheet.name.desc=no description",
        "LocalFilesDataSourceNode.createSheet.noDesc=no description",})
    protected Sheet createSheet() {
        Sheet sheet = new Sheet();
        Sheet.Set sheetSet = Sheet.createPropertiesSet();
        sheet.put(sheetSet);

        sheetSet.put(new NodeProperty<>(Bundle.LocalFilesDataSourceNode_createSheet_name_name(),
                Bundle.LocalFilesDataSourceNode_createSheet_name_displayName(),
                Bundle.LocalFilesDataSourceNode_createSheet_name_desc(),
                getName()));

        sheetSet.put(new NodeProperty<>(Bundle.LocalFilesDataSourceNode_createSheet_type_name(),
                Bundle.LocalFilesDataSourceNode_createSheet_type_displayName(),
                Bundle.LocalFilesDataSourceNode_createSheet_type_desc(),
                Bundle.LocalFilesDataSourceNode_createSheet_type_text()));

        sheetSet.put(new NodeProperty<>(Bundle.LocalFilesDataSourceNode_createSheet_size_name(),
                Bundle.LocalFilesDataSourceNode_createSheet_size_displayName(),
                Bundle.LocalFilesDataSourceNode_createSheet_size_desc(),
                this.content.getSize()));

        sheetSet.put(new NodeProperty<>(Bundle.LocalFilesDataSourceNode_createSheet_timezone_name(),
                Bundle.LocalFilesDataSourceNode_createSheet_timezone_displayName(),
                Bundle.LocalFilesDataSourceNode_createSheet_timezone_desc(),
                ""));

        sheetSet.put(new NodeProperty<>(Bundle.LocalFilesDataSourceNode_createSheet_deviceId_name(),
                Bundle.LocalFilesDataSourceNode_createSheet_deviceId_displayName(),
                Bundle.LocalFilesDataSourceNode_createSheet_deviceId_desc(),
                localFileDataSource.getDeviceId()));

        return sheet;
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
