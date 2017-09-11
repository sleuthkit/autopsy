/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import java.util.Objects;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import org.openide.util.NbBundle;

/**
 * Filter for text matching
 */
public class TextFilter extends AbstractFilter {

    public TextFilter() {
    }

    public TextFilter(String text) {
        this.text.set(text);
    }

    private final SimpleStringProperty text = new SimpleStringProperty();

    synchronized public void setText(String text) {
        this.text.set(text);
    }

    @Override
    @NbBundle.Messages("TextFilter.displayName.text=Text Filter")
    public String getDisplayName() {
        return Bundle.TextFilter_displayName_text();
    }

    synchronized public String getText() {
        return text.getValue();
    }

    public Property<String> textProperty() {
        return text;
    }

    @Override
    synchronized public TextFilter copyOf() {
        TextFilter textFilter = new TextFilter(getText());
        textFilter.setSelected(isSelected());
        textFilter.setDisabled(isDisabled());
        return textFilter;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TextFilter other = (TextFilter) obj;

        if (isSelected() != other.isSelected()) {
            return false;
        }
        return Objects.equals(text.get(), other.text.get());
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.text.get());
        return hash;
    }
}
