/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.awt.Component;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import javax.swing.table.TableCellRenderer;
import org.netbeans.swing.outline.Outline;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.datamodel.Account;

/**
 * Utility class with helpers for dealing with accounts.
 */
public final class Utils {

    private Utils() {
    }

    static public ZoneId getUserPreferredZoneId() {
        ZoneId zone = UserPreferences.displayTimesInLocalTime() ?
                ZoneOffset.systemDefault() : TimeZone.getTimeZone(UserPreferences.getTimeZoneForDisplays()).toZoneId();
        return zone;
    }

    /**
     * Get the path of the icon for the given Account Type.
     *
     * @return The path of the icon for the given Account Type.
     */
    static public final String getIconFilePath(Account.Type type) {
        return Accounts.getIconFilePath(type);
    }
    
    static public  void setColumnWidths(Outline outline) {
        int margin = 4;
        int padding = 8;

        final int rows = Math.min(100, outline.getRowCount());

        for (int column = 0; column < outline.getColumnCount(); column++) {
            int columnWidthLimit = 500;
            int columnWidth = 0;

            // find the maximum width needed to fit the values for the first 100 rows, at most
            for (int row = 0; row < rows; row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, column);
                Component comp = outline.prepareRenderer(renderer, row, column);
                columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
            }

            columnWidth += 2 * margin + padding; // add margin and regular padding
            columnWidth = Math.min(columnWidth, columnWidthLimit);

            outline.getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
        }
    }

}
