/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import org.sleuthkit.datamodel.FsContent;

/**
 * Abstract class that implements the commonality between File and Directory
 * Nodes (same properties).
 */
abstract class AbstractFsContentNode<T extends FsContent> extends AbstractContentNode<T> {

    /**
     * Name of the property that holds the name.
     */
    public static final String PROPERTY_NAME = "Name";
    /**
     * Name of the property that holds the path.
     */
    public static final String PROPERTY_LOCATION = "Location";

    AbstractFsContentNode(T fsContent) {
        super(fsContent);
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        // Note: this order matters for the search result, changed it if the order of property headers on the "KeywordSearchNode"changed
        ss.put(new NodeProperty(PROPERTY_NAME, "Name", "no description", content.getName()));
        ss.put(new NodeProperty(PROPERTY_LOCATION, "Location", "no description", DataConversion.getformattedPath(ContentUtils.getDisplayPath(content), 0)));
        ss.put(new NodeProperty("Modified Time", "Modified Time", "no description", content.getMtimeAsDate()));
        ss.put(new NodeProperty("Changed Time", "Changed Time", "no description", content.getCtimeAsDate()));
        ss.put(new NodeProperty("Access Time", "Access Time", "no description", content.getAtimeAsDate()));
        ss.put(new NodeProperty("Created Time", "Created Time", "no description", content.getCrtimeAsDate()));
        ss.put(new NodeProperty("Size", "Size", "no description", content.getSize()));
        ss.put(new NodeProperty("Flags (Directory)", "Flags (Directory)", "no description", content.getDirFlagsAsString()));
        ss.put(new NodeProperty("Flags (Meta)", "Flags (Meta)", "no description", content.getMetaFlagsAsString()));
        ss.put(new NodeProperty("Mode ", "Mode", "no description", content.getModeAsString()));
        ss.put(new NodeProperty("User ID", "User ID", "no description", content.getUid()));
        ss.put(new NodeProperty("Group ID", "Group ID", "no description", content.getGid()));
        ss.put(new NodeProperty("Metadata Address", "Metadata Addr", "no description", content.getMeta_addr()));
        ss.put(new NodeProperty("Attribute Address", "Attribute Addr", "no description", Long.toString(content.getAttr_type()) + "-" + Long.toString(content.getAttr_id())));
        ss.put(new NodeProperty("Type (Directory)", "Type (Directory)", "no description", content.getDirTypeAsString()));
        ss.put(new NodeProperty("Type (Meta)", "Type (Meta)", "no description", content.getMetaTypeAsString()));
        ss.put(new NodeProperty("Known", "Known", "no description", content.getKnown().getName()));

        return s;
    }
}
