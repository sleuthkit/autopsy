/* UNCLASSIFIED
 *
 *  Viking
 *
 *  Copyright (c) 2013-2015 Basis Technology Corporation.
 *  Contact: brianc@basistech.com
 */
package viking.autoingest;

import java.awt.Component;
import java.time.Duration;
import javax.swing.JTable;
import static javax.swing.SwingConstants.CENTER;

/**
 * A JTable cell renderer that renders a duration represented as a long as a
 * string with days, hours, minutes, and seconds components. It center-aligns
 * cell content and grays out the cell if the table is disabled.
 */
class DurationCellRenderer extends GrayableCellRenderer {

    private static final long serialVersionUID = 1L;

    DurationCellRenderer() {
        setHorizontalAlignment(CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof Long) {
            {
                Duration d = Duration.ofMillis((long) value);
                if (d.isNegative()) {
                    d = Duration.ofMillis(-(long) value);
                }

                String result;
                long days = d.toDays();
                long hours = d.minusDays(days).toHours();
                long minutes = d.minusDays(days).minusHours(hours).toMinutes();
                long seconds = d.minusDays(days).minusHours(hours).minusMinutes(minutes).getSeconds();

                if (minutes > 0) {
                    if (hours > 0) {
                        if (days > 0) {
                            result = days + " d  " + hours + " h  " + minutes + " m " + seconds + " s";
                        } else {
                            result = hours + " h  " + minutes + " m " + seconds + " s";
                        }
                    } else {
                        result = minutes + " m " + seconds + " s";
                    }
                } else {
                    result = seconds + " s";
                }

                setText(result);
            }
        }
        grayCellIfTableNotEnabled(table, isSelected);
        return this;
    }
}
