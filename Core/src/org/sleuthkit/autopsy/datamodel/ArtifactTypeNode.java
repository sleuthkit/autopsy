/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2014 Basis Technology Corp.
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

import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Node encapsulating blackboard artifact type. This is used on the left-hand
 * navigation side of the Autopsy UI as the parent node for all of the artifacts
 * of a given type. Its children will be BlackboardArtifactNode objects.
 */
public class ArtifactTypeNode extends DisplayableItemNode {

    private BlackboardArtifact.ARTIFACT_TYPE type;
    private long childCount = 0;

    ArtifactTypeNode(BlackboardArtifact.ARTIFACT_TYPE type, SleuthkitCase skCase) {
        super(Children.create(new ArtifactTypeChildren(type, skCase), true), Lookups.singleton(type.getDisplayName()));
        super.setName(type.getLabel());
        // NOTE: This completely destroys our lazy-loading ideal
        //    a performance increase might be had by adding a 
        //    "getBlackboardArtifactCount()" method to skCase
        try {
            this.childCount = skCase.getBlackboardArtifactsTypeCount(type.getTypeID());
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

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.name"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.displayName"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.artType.desc"),
                type.getDisplayName()));

        ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.name"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.displayName"),
                NbBundle.getMessage(this.getClass(), "ArtifactTypeNode.createSheet.childCnt.desc"),
                childCount));

        return s;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    // @@@ TODO: Merge with BlackboartArtifactNode.getIcon()
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
            case TSK_CONTACT:
                return "contact.png";
            case TSK_MESSAGE:
                return "message.png";
            case TSK_CALLLOG:
                return "calllog.png";
            case TSK_CALENDAR_ENTRY:
                return "calendar.png";
            case TSK_SPEED_DIAL_ENTRY:
                return "speeddialentry.png";
            case TSK_BLUETOOTH_PAIRING:
                return "bluetooth.png";
            case TSK_GPS_BOOKMARK:
                return "gpsfav.png";
            case TSK_GPS_LAST_KNOWN_LOCATION:
                return "gps-lastlocation.png";
            case TSK_GPS_SEARCH:
                return "gps-search.png";
            case TSK_SERVICE_ACCOUNT:
                return "account-icon-16.png";
            case TSK_ENCRYPTION_DETECTED:
                return "encrypted-file.png";
            case TSK_EXT_MISMATCH_DETECTED:
                return "mismatch-16.png";
        }
        return "artifact-icon.png";
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}
