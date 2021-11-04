/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils;

import java.awt.Component;
import java.time.Duration;
import javax.swing.JTable;

/**
 * A JTable cell renderer that renders a duration represented as a long as a
 * string with days, hours, minutes, and seconds components. It center-aligns
 * cell content and grays out the cell if the table is disabled.
 */
public final class DurationCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;
    private static final char UNIT_SEPARATOR_CHAR = ':';

    public DurationCellRenderer() {
        setHorizontalAlignment(LEFT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Long) {
            setText(DurationCellRenderer.longToDurationString((long) value));
        }
        grayCellIfTableNotEnabled(table, isSelected);
        return this;
    }

    public static char getUnitSeperator() {
        return UNIT_SEPARATOR_CHAR;
    }

    /**
     * Convert a duration represented by a long to a human readable string with
     * with days, hours, minutes, and seconds components.
     *
     * @param duration - The representation of the duration in long form.
     *
     * @return - The representation of the duration in String form.
     */
    public static String longToDurationString(long duration) {
        Duration d = Duration.ofMillis(duration);
        if (d.isNegative()) {
            d = Duration.ofMillis(0); //it being 0 for a few seconds seems preferable to it counting down to 0 then back up from 0
        }
        long days = d.toDays();
        long hours = d.minusDays(days).toHours();
        long minutes = d.minusDays(days).minusHours(hours).toMinutes();
        long seconds = d.minusDays(days).minusHours(hours).minusMinutes(minutes).getSeconds();
        if (days < 0) {
            days = 0;
        }
        if (hours < 0) {
            hours = 0;
        }
        if (minutes < 0) {
            minutes = 0;
        }
        if (seconds < 0) {
            seconds = 0;
        }
        StringBuilder results = new StringBuilder(12);
        if (days < 99) {
            results.append(String.format("%02d", days));
        } else {
            results.append(days); //in the off chance something has been running for over 99 days lets allow it to stand out a bit by having as many characters as it needs
        }
        results.append(UNIT_SEPARATOR_CHAR);
        results.append(String.format("%02d", hours));
        results.append(UNIT_SEPARATOR_CHAR);
        results.append(String.format("%02d", minutes));
        results.append(UNIT_SEPARATOR_CHAR);
        results.append(String.format("%02d", seconds));
        return results.toString();
    }

}
