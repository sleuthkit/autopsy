/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 * Filters file date properties (modified/created/etc.. times)
 *
 * @author pmartel
 */
class DateSearchFilter extends AbstractFileSearchFilter<DateSearchPanel> {

    private static final String NONE_SELECTED_MESSAGE = NbBundle.getMessage(DateSearchFilter.class, "DateSearchFilter.noneSelectedMsg.text");
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
    private static final String SEPARATOR = "SEPARATOR"; //NON-NLS

    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE,
            Case.Events.DATA_SOURCE_ADDED, Case.Events.DATA_SOURCE_DELETED);

    /**
     * New DateSearchFilter with the default panel
     */
    DateSearchFilter() {
        this(new DateSearchPanel(DATE_FORMAT, DateSearchFilter.createTimeZoneList()));
    }

    private DateSearchFilter(DateSearchPanel panel) {
        super(panel);
        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, this.new CasePropertyChangeListener());
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().getDateCheckBox().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        String query = "NULL";
        DateSearchPanel panel = this.getComponent();

        // convert the date from the selected timezone to get the GMT
        long fromDate = 0;
        String startDateValue = panel.getFromDate();
        Calendar startDate = getCalendarDate(startDateValue);
        if (!startDateValue.isEmpty()) {
            if (startDate != null) {
                fromDate = startDate.getTimeInMillis() / 1000; // divided by 1000 because we want to get the seconds, not miliseconds
            }
        }

        long toDate = 0;
        String endDateValue = panel.getToDate();
        Calendar endDate = getCalendarDate(endDateValue);
        if (!endDateValue.isEmpty()) {
            if (endDate != null) {
                toDate = endDate.getTimeInMillis() / 1000; // divided by 1000 because we want to get the seconds, not miliseconds
            }
        }

        final boolean modifiedChecked = panel.getModifiedCheckBox().isSelected();
        final boolean changedChecked = panel.getChangedCheckBox().isSelected();
        final boolean accessedChecked = panel.getAccessedCheckBox().isSelected();
        final boolean createdChecked = panel.getCreatedCheckBox().isSelected();

        if (modifiedChecked || changedChecked || accessedChecked || createdChecked) {

            if (modifiedChecked) {
                query += " OR (mtime BETWEEN " + fromDate + " AND " + toDate + ")"; //NON-NLS
            }

            if (changedChecked) {
                query += " OR (ctime BETWEEN " + fromDate + " AND " + toDate + ")"; //NON-NLS
            }

            if (accessedChecked) {
                query += " OR (atime BETWEEN " + fromDate + " AND " + toDate + ")"; //NON-NLS
            }

            if (createdChecked) {
                query += " OR (crtime BETWEEN " + fromDate + " AND " + toDate + ")"; //NON-NLS
            }

        } else {
            throw new FilterValidationException(NONE_SELECTED_MESSAGE);
        }

        return query;

    }

    private void updateTimeZoneList() {
        this.getComponent().setTimeZones(DateSearchFilter.createTimeZoneList());
    }

    private static List<String> createTimeZoneList() {

        List<String> timeZones = new ArrayList<>();

        try {
            // get the latest case
            Case currentCase = Case.getOpenCase(); // get the most updated case

            Set<TimeZone> caseTimeZones = currentCase.getTimeZones();
            Iterator<TimeZone> iterator = caseTimeZones.iterator();
            while (iterator.hasNext()) {
                TimeZone zone = iterator.next();
                int offset = zone.getRawOffset() / 1000;
                int hour = offset / 3600;
                int minutes = (offset % 3600) / 60;
                String item = String.format("(GMT%+d:%02d) %s", hour, minutes, zone.getID()); //NON-NLS
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
                String item = String.format("(GMT%+d:%02d) %s", hour, minutes, id); //NON-NLS
                timeZones.add(item);
            }
        } catch (NoCurrentCaseException ex) {
            // No current case.
        }

        return timeZones;
    }

    private TimeZone getSelectedTimeZone() {
        String tz = this.getComponent().getTimeZoneComboBox().getSelectedItem().toString();
        String tzID = tz.substring(tz.indexOf(" ") + 1); // 1 index after the space is the ID
        TimeZone selectedTZ = TimeZone.getTimeZone(tzID); //
        return selectedTZ;
    }
    
    private Calendar getCalendarDate(String dateValue) {
        TimeZone selectedTZ = getSelectedTimeZone();
        Calendar inputDate = null;
        try {
            DateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            sdf.setTimeZone(selectedTZ); // get the time in the selected timezone
            Date temp = sdf.parse(dateValue);

            inputDate = Calendar.getInstance(new SimpleTimeZone(0, "GMT")); //NON-NLS
            inputDate.setTime(temp); // convert to GMT
        } catch (ParseException ex) {
            // for now, no need to show the error message to the user here
        }
        return inputDate;
    }
      
    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addDateChangeListener();
    }

    @Override
    @Messages ({
        "DateSearchFilter.errorMessage.endDateBeforeStartDate=The end date should be after the start date.",
        "DateSearchFilter.errorMessage.noCheckboxSelected=At least one date type checkbox must be selected."
    })
    public boolean isValid() {

        DateSearchPanel panel = this.getComponent();
        Calendar startDate = getCalendarDate(panel.getFromDate());
        Calendar endDate = getCalendarDate(panel.getToDate());
        
        if ((startDate != null && startDate.after(endDate)) || (endDate != null && endDate.before(startDate)))  {
            setLastError(Bundle.DateSearchFilter_errorMessage_endDateBeforeStartDate());
            return false;
        }
        
        if (!panel.isValidSearch()) {
            setLastError(Bundle.DateSearchFilter_errorMessage_noCheckboxSelected());
            return false;
        }
        
        return true;
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
            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE:
                    Object newValue = evt.getNewValue();
                    if (null != newValue) {
                        /**
                         * Opening a new case.
                         */
                        SwingUtilities.invokeLater(DateSearchFilter.this::updateTimeZoneList);
                    }
                    break;
                case DATA_SOURCE_ADDED:
                case DATA_SOURCE_DELETED:
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        SwingUtilities.invokeLater(DateSearchFilter.this::updateTimeZoneList);
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                    break;
            }
        }
    }
}
