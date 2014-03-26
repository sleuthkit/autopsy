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
package org.sleuthkit.autopsy.filesearch;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Filters file date properties (modified/created/etc.. times)
 * @author pmartel
 */
class DateSearchFilter extends AbstractFileSearchFilter<DateSearchPanel> {

    private static final String NONE_SELECTED_MESSAGE = NbBundle.getMessage(DateSearchFilter.class, "DateSearchFilter.noneSelectedMsg.text");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    private static final String SEPARATOR = "SEPARATOR";

    /**
     * New DateSearchFilter with the default panel
     */
    DateSearchFilter() {
        this(new DateSearchPanel(DATE_FORMAT, DateSearchFilter.createTimeZoneList()));
    }

    private DateSearchFilter(DateSearchPanel panel) {
        super(panel);
        Case.addPropertyChangeListener(this.new CasePropertyChangeListener());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().getDateCheckBox().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String addQuery = "1";
        DateSearchPanel panel = this.getComponent();

        // first, get the selected timeZone from the dropdown list
        String tz = this.getComponent().getTimeZoneComboBox().getSelectedItem().toString();
        String tzID = tz.substring(tz.indexOf(" ") + 1); // 1 index after the space is the ID
        TimeZone selectedTZ = TimeZone.getTimeZone(tzID); //

        // convert the date from the selected timezone to get the GMT
        long fromDate = 0;
        String startDateValue = panel.getDateFromTextField().getText();
        Calendar startDate = null;
        try {
            DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            sdf.setTimeZone(selectedTZ); // get the time in the selected timezone
            Date temp = sdf.parse(startDateValue);

            startDate = Calendar.getInstance(new SimpleTimeZone(0, "GMT"));
            startDate.setTime(temp); // convert to GMT
        } catch (ParseException ex) {
            // for now, no need to show the error message to the user her
        }
        if (!startDateValue.equals("")) {
            if (startDate != null) {
                fromDate = startDate.getTimeInMillis() / 1000; // divided by 1000 because we want to get the seconds, not miliseconds
            }
        }

        long toDate = 0;
        String endDateValue = panel.getDateToTextField().getText();
        Calendar endDate = null;
        try {
            DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            sdf.setTimeZone(selectedTZ); // get the time in the selected timezone
            Date temp2 = sdf.parse(endDateValue);

            endDate = Calendar.getInstance(new SimpleTimeZone(0, "GMT"));
            endDate.setTime(temp2); // convert to GMT
            endDate.set(Calendar.HOUR, endDate.get(Calendar.HOUR) + 24); // get the next 24 hours
        } catch (ParseException ex) {
            // for now, no need to show the error message to the user here
        }
        if (!endDateValue.equals("")) {
            if (endDate != null) {
                toDate = endDate.getTimeInMillis() / 1000; // divided by 1000 because we want to get the seconds, not miliseconds
            }
        }

        final boolean modifiedChecked = panel.getModifiedCheckBox().isSelected();
        final boolean changedChecked = panel.getChangedCheckBox().isSelected();
        final boolean accessedChecked = panel.getAccessedCheckBox().isSelected();
        final boolean createdChecked = panel.getAccessedCheckBox().isSelected();


        if (modifiedChecked || changedChecked || accessedChecked || createdChecked) {

            String subQuery = "0";

            if (modifiedChecked) {
                subQuery += " or mtime between " + fromDate + " and " + toDate;
            }

            if (changedChecked) {
                subQuery += " or ctime between " + fromDate + " and " + toDate;
            }

            if (accessedChecked) {
                subQuery += " or atime between " + fromDate + " and " + toDate;
            }

            if (createdChecked) {
                subQuery += " or crtime between " + fromDate + " and " + toDate;
            }

            addQuery += " and (" + subQuery + ")";
        } else {
            throw new FilterValidationException(NONE_SELECTED_MESSAGE);
        }

        return addQuery;

    }

    private void updateTimeZoneList() {
        this.getComponent().setTimeZones(DateSearchFilter.createTimeZoneList());
    }

    private static List<String> createTimeZoneList() {

        List<String> timeZones = new ArrayList<String>();

        if (Case.existsCurrentCase()) {
            // get the latest case
            Case currentCase = Case.getCurrentCase(); // get the most updated case

            Set<TimeZone> caseTimeZones = currentCase.getTimeZone();
            Iterator<TimeZone> iterator = caseTimeZones.iterator();
            while (iterator.hasNext()) {
                TimeZone zone = iterator.next();
                int offset = zone.getRawOffset() / 1000;
                int hour = offset / 3600;
                int minutes = (offset % 3600) / 60;
                String item = String.format("(GMT%+d:%02d) %s", hour, minutes, zone.getID());
                timeZones.add(item);
            }

            if (caseTimeZones.size() > 0) {
                timeZones.add(SEPARATOR);
            }

            // load and add all timezone
            String[] ids = SimpleTimeZone.getAvailableIDs();
            for (String id : ids) {
                TimeZone zone = TimeZone.getTimeZone(id);
                int offset = zone.getRawOffset() / 1000;
                int hour = offset / 3600;
                int minutes = (offset % 3600) / 60;
                String item = String.format("(GMT%+d:%02d) %s", hour, minutes, id);
                timeZones.add(item);
            }
        }

        return timeZones;
    }

    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addActionListener(l);
    }

    /**
     * Inner class to put the separator inside the combo box.
     */
    static class ComboBoxRenderer extends JLabel implements ListCellRenderer<String> {

        JSeparator separator;

        ComboBoxRenderer() {
            setOpaque(true);
            setBorder(new EmptyBorder(1, 1, 1, 1));
            separator = new JSeparator(JSeparator.HORIZONTAL);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
            String str = (value == null) ? "" : value;
            if (SEPARATOR.equals(str)) {
                return separator;
            }
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            setFont(list.getFont());
            setText(str);
            return this;
        }
    }

    private class CasePropertyChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String changed = evt.getPropertyName();
            Object oldValue = evt.getOldValue();
            Object newValue = evt.getNewValue();

            if (changed.equals(Case.Events.CURRENT_CASE.toString().toString())) {
                // create or open a case
                if (newValue != null) {
                    DateSearchFilter.this.updateTimeZoneList();
                }
            }

            // if the image is added to the case
            if (changed.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                DateSearchFilter.this.updateTimeZoneList();
            }

            // if the image is removed from the case
            if (changed.equals(Case.Events.DATA_SOURCE_DELETED.toString())) {
                DateSearchFilter.this.updateTimeZoneList();
            }
        }
    }
}
