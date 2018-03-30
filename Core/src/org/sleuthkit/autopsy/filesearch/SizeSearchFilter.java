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

import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.Locale;
import javax.swing.JComboBox;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.python.icu.text.NumberFormat;
import org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException;

/**
 * Filter search size input.
 */
class SizeSearchFilter extends AbstractFileSearchFilter<SizeSearchPanel> {

    /**
     * Instantiate a SizeSearchFilter object for a new SizeSearchPanel.
     */
    SizeSearchFilter() {
        this(new SizeSearchPanel());
    }

    /**
     * Instantiate a SizeSearchFilter object for an existing SizeSearchPanel.
     *
     * @param component The SizeSearchPanel instance.
     */
    SizeSearchFilter(SizeSearchPanel component) {
        super(component);
    }

    @Override
    public boolean isEnabled() {
        return this.getComponent().getSizeCheckBox().isSelected();
    }

    @Override
    public String getPredicate() throws FilterValidationException {
        int size = ((Number) this.getComponent().getSizeTextField().getValue()).intValue(); // note: already only allow number to the text field
        String operator = compareComboBoxToOperator(this.getComponent().getSizeCompareComboBox());
        int unit = this.getComponent().getSizeUnitComboBox().getSelectedIndex();
        int divider = (int) Math.pow(2, (unit * 10));
        size = size * divider;
        return "size " + operator + " " + size; //NON-NLS
    }

    /**
     * Get the comparison operator associated with the size comparison
     * drop-down selection.
     *
     * @param compare The drop-down component.
     *
     * @return The operator.
     */
    private String compareComboBoxToOperator(JComboBox<String> compare) {
        String compareSize = compare.getSelectedItem().toString();

        if (compareSize.equals(NbBundle.getMessage(this.getClass(), "SizeSearchPanel.sizeCompareComboBox.equalTo"))) {
            return "=";
        } else if (compareSize.equals(
                NbBundle.getMessage(this.getClass(), "SizeSearchPanel.sizeCompareComboBox.greaterThan"))) {
            return ">";
        } else if (compareSize.equals(
                NbBundle.getMessage(this.getClass(), "SizeSearchPanel.sizeCompareComboBox.lessThan"))) {
            return "<";
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addActionListener(l);
    }

    @Override
    @Messages({
        "SizeSearchFilter.errorMessage.nonNegativeNumber=Input size data is a negative number.",
        "SizeSearchFilter.errorMessage.notANumber=Input size data is not a number."
    })
    public boolean isValid() {
        String input = this.getComponent().getSizeTextField().getText();

        try {
            int inputInt = NumberFormat.getNumberInstance(Locale.US).parse(input).intValue();
            if (inputInt < 0) {
                setLastError(Bundle.SizeSearchFilter_errorMessage_nonNegativeNumber());
                return false;
            }
        } catch (ParseException ex) {
            setLastError(Bundle.SizeSearchFilter_errorMessage_notANumber());
            return false;
        }
        return true;
    }
}
