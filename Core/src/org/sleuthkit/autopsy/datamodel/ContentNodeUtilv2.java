/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.util.Date;
import java.util.List;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.ThreePanelDAO.ColumnKey;
import org.sleuthkit.datamodel.Content;

/**
 *
 * @author gregd
 */
public class ContentNodeUtilv2 {

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

    public static Children getChildren(long id) {
        throw new UnsupportedOperationException("Not supported...");
        //return Children.create(new ContentChildren(content), true);
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
}
