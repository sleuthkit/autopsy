/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2015 Basis Technology Corp.
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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;

/**
 * A DisplayableItem is any node in the Autopsy directory tree. All of the nodes
 * in the tree will eventually extend this. This includes the data source,
 * views, extracted results, etc. areas.
 */
public abstract class DisplayableItemNode extends AbstractNode {

    public DisplayableItemNode(Children children) {
        super(children);
    }

    public DisplayableItemNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    public abstract boolean isLeafTypeNode();

    public abstract <T> T accept(DisplayableItemNodeVisitor<T> v);
    
    public abstract ItemType getItemType();
    
    public enum ItemType {
        GENERIC("Generic"),
        BLACKBOARD_ARTIFACT("BlackboardArtifact"),
        BLACKBOARD_ARTIFACT_TAG("BlackboardArtifactTag"),
        CONTENT_TAG("ContentTag"),
        DATA_SOURCES("DataSources"),
        DELETED_CONTENT("DeletedContent"),
        DELETED_CONTENT_CHILDREN("DeletedContentChildren"),
        DIRECTORY("Directory"),
        EMAIL_EXTRACTED_ROOT("EmailExtractedRoot"),
        EMAIL_EXTRACTED_ACCOUNT("EmailExtractedAccount"),
        EMAIL_EXTRACTED_FOLDER("EmailExtractedFolder"),
        EXTRACTED_CONTENT_ROOT("ExtractedContentRoot"),
        EXTRACTED_CONTENT_TYPE("ExtractedContentType"),
        EXTRACTED_BOOKMARKS("ExtractedBookmarks"),
        EXTRACTED_COOKIES("ExtractedCookies"),
        EXTRACTED_HISTORY("ExtractedHistory"),
        EXTRACTED_DOWNLOADS("ExtractedDownloads"),
        EXTRACTED_PROGRAMS("ExtractedPrograms"),
        EXTRACTED_RECENT("ExtractedRecent"),
        EXTRACTED_ATTACHED_DEVICES("ExtractedAttachedDevices"),
        EXTRACTED_SEARCH("ExtractedSearch"),
        EXTRACTED_METADATA_EXIF("ExtractedMetadataExif"),
        EXTRACTED_EMAIL_MSG("ExtractedEmailMsg"),
        EXTRACTED_CONTACTS("ExtractedContacts"),
        EXTRACTED_MESSAGES("ExtractedMessages"),
        EXTRACTED_CALL_LOG("ExtractedCallLog"),
        EXTRACTED_CALENDAR("ExtractedCalendar"),
        EXTRACTED_SPEED_DIAL("ExtractedSpeedDial"),
        EXTRACTED_BLUETOOTH("ExtractedBluetooth"),
        EXTRACTED_GPS_BOOKMARKS("ExtractedGPSBookmarks"),
        EXTRACTED_GPS_LAST_LOCATION("ExtractedGPSLastLocation"),
        EXTRACTED_GPS_SEARCH("ExtractedGPSSearch"),
        EXTRACTED_SERVICE_ACCOUNT("ExtractedServiceAccount"),
        EXTRACTED_ENCRYPTION("ExtractedEncryption"),
        EXTRACTED_EXT_MISMATCH("ExtractedExtMismatch"),
        EXTRACTED_OS("ExtractedOS"),
        EXTRACTED_FACE_DETECTED("ExtractedFaceDetected"),
        FILE("File"),
        FILE_SIZE_ROOT("FileSizeRoot"),
        FILE_SIZE("FileSize"),
        FILE_TYPE("FileType"),
        FILE_TYPES("FileTypes"),
        FILE_TYPES_DOC("FileTypesDoc"),
        FILE_TYPES_EXE("FileTypesExe"),
        HASHSET_ROOT("HashsetRoot"),
        HASHSET_NAME("HashsetName"),
        IMAGE("Image"),
        INTERESTING_HITS_ROOT("InterestingHitsRoot"),
        INTERESTING_HITS_SET_NAME("InterestingHitsSetName"),
        KEYWORD_ROOT("KeywordRoot"),
        KEYWORD_LIST("KeywordList"),
        KEYWORD_TERM("KeywordTerm"),
        LAYOUT_FILE("LayoutFile"),
        LOCAL_FILE("LocalFile"),
        RECENT_FILES("RecentFiles"),
        RECENT_FILES_FILTER("RecentFilesFilter"),
        REPORTS("Reports"),
        REPORTS_LIST("ReportsList"),
        RESULTS("Results"),
        TAGS_ROOT("TagsRoots"),
        TAGS_NAME("TagsName"),
        TAGS_CONTENT_TYPE("TagsContentType"),
        TAGS_BLACKBOARD_ARTIFACT("TagsBlackboardArtifact"),
        VIEWS("Views"),
        VIRTUAL_DIRECTORY("VirtualDirectory"),
        VOLUME("Volume"),
        EVENT("Event"),
        EVENT_ROOT("EventRoot");

        private final String name;
        private ItemType(String name) {
            this.name = name;
        }
        @Override
        public String toString() {
            return this.name;
        }
    }
}
