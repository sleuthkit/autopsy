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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for setting up nodes that handle content.
 */
public class ContentNodeUtil {

    public static String getContentDisplayName(String fileName) {
        switch (fileName) {
            case "..":
                return DirectoryNode.DOTDOTDIR;
            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return fileName;
        }
    }

    public static String getContentName(long objId) {
        return "content_" + Long.toString(objId);
    }

    public static Lookup getLookup(Content content) {
        return Lookups.fixed(content, new TskContentItem<>(content));
    }

    public static Sheet setSheet(Sheet sheet, List<ColumnKey> columnKeys, List<Object> values) {
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        int maxSize = Math.min(columnKeys.size(), values.size());

        for (int i = 0; i < maxSize; i++) {
            ColumnKey columnKey = columnKeys.get(i);
            Object cellValue = values.get(i);

            if (cellValue == null) {
                sheetSet.put(new NodeProperty<>(
                    columnKey.getFieldName(),
                    columnKey.getDisplayName(),
                    columnKey.getDescription(),
                    ""
                ));
                continue;
            }

            if (cellValue instanceof Date) {
                cellValue = TimeZoneUtils.getFormattedTime(((Date) cellValue).getTime() / 1000);
            }

            sheetSet.put(new NodeProperty<>(
                    columnKey.getFieldName(),
                    columnKey.getDisplayName(),
                    columnKey.getDescription(),
                    cellValue
            ));
        }

        return sheet;
    }
    
    /**
     * Get all tags from the case database that are associated with the file
     *
     * @return a list of tags that are associated with the file
     */
    public static List<ContentTag> getContentTagsFromDatabase(Content content) throws TskCoreException, NoCurrentCaseException{
        List<ContentTag> tags = new ArrayList<>();
        tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(content));
       
        return tags;
    }
    
    public static List<BlackboardArtifactTag> getArtifactTagsFromDatabase(BlackboardArtifact artifact)  throws TskCoreException, NoCurrentCaseException{
        List<BlackboardArtifactTag> tags = new ArrayList<>();
        tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact));
        return tags;
    }
}
