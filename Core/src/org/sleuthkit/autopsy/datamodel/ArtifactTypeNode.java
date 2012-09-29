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

import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Node encapsulating blackboard artifact type
 */
public class ArtifactTypeNode extends DisplayableItemNode {

    BlackboardArtifact.ARTIFACT_TYPE type;
    int childCount = 0;

    ArtifactTypeNode(BlackboardArtifact.ARTIFACT_TYPE type, SleuthkitCase skCase) {
        super(Children.create(new ArtifactTypeChildren(type, skCase), true), Lookups.singleton(type.getDisplayName()));
        super.setName(type.getLabel());
        // NOTE: This completely destroys our lazy-loading ideal
        //    a performance increase might be had by adding a 
        //    "getBlackboardArtifactCount()" method to skCase
        try {
            this.childCount = skCase.getBlackboardArtifacts(type.getTypeID()).size();
        } catch (TskException ex) {
            Logger.getLogger(ArtifactTypeNode.class.getName())
                    .log(Level.WARNING, "Error getting child count", ex);
        }
        super.setDisplayName(type.getDisplayName() + " (" + childCount + ")");
        this.type = type;
        this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/" + getIcon(type));

    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }

        ss.put(new NodeProperty("Artifact Type",
                "Artifact Type",
                "no description",
                type.getDisplayName()));

        ss.put(new NodeProperty("Child Count",
                "Child Count",
                "no description",
                childCount));

        return s;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    private String getIcon(BlackboardArtifact.ARTIFACT_TYPE type) {
        switch (type) {
            case TSK_WEB_BOOKMARK:
                return "bookmarks.png";
            case TSK_WEB_COOKIE:
                return "cookies.png";
            case TSK_WEB_HISTORY:
                return "history.png";
            case TSK_WEB_DOWNLOAD:
                return "downloads.png";
            case TSK_INSTALLED_PROG:
                return "programs.png";
            case TSK_RECENT_OBJECT:
                return "recent_docs.png";
            case TSK_DEVICE_ATTACHED:
                return "usb_devices.png";
            case TSK_WEB_SEARCH_QUERY:
                return "searchquery.png";
            case TSK_METADATA_EXIF:
                return "camera-icon-16.png";
        }
        return "artifact-icon.png";
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.META;
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
