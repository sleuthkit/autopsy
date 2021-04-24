/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import org.apache.commons.lang3.StringUtils;

/**
 * A label that allows for displaying loading messages and can be used with a
 * DataFetchResult. Text displays as "<key>:<value | message>".
 */
public class LoadableLabel extends AbstractLoadableComponent<String> {

    private static final long serialVersionUID = 1L;

    private final JLabel label = new JLabel();
    private final String key;

    /**
     * Main constructor for the label.
     *
     * @param key The key to be displayed.
     */
    public LoadableLabel(String key) {
        this.key = key;
        setLayout(new BorderLayout());
        add(label, BorderLayout.CENTER);
        this.showResults(null);
    }

    private void setValue(String value) {
        String formattedKey = StringUtils.isBlank(key) ? "" : key;
        String formattedValue = StringUtils.isBlank(value) ? "" : value;
        label.setText(String.format("%s: %s", formattedKey, formattedValue));
    }

    @Override
    protected void setMessage(boolean visible, String message) {
        setValue(message);
    }

    @Override
    protected void setResults(String data) {
        setValue(data);
    }
}
