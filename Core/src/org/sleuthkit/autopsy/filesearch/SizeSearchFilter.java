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

import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import org.sleuthkit.autopsy.filesearch.FileSearchFilter.FilterValidationException;

/**
 *
 * @author pmartel
 */
class SizeSearchFilter extends AbstractFileSearchFilter<SizeSearchPanel> {

    SizeSearchFilter() {
        this(new SizeSearchPanel());
    }

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
        return "size " + operator + " " + size;
    }

    private String compareComboBoxToOperator(JComboBox<String> compare) {
        String compareSize = compare.getSelectedItem().toString();

        if (compareSize.equals("equal to")) {
            return "=";
        } else if (compareSize.equals("greater than")) {
            return ">";
        } else if (compareSize.equals("less than")) {
            return "<";
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void addActionListener(ActionListener l) {
        getComponent().addActionListener(l);
    }
}
