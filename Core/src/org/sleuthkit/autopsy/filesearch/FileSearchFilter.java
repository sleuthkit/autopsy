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
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;

/**
 * Provides a filter and the panel to display it to the FileSearchTopComponent
 */
interface FileSearchFilter {

    /**
     * Gets the panel to put in the File Search pane.
     *
     * @return component that provides input to filter
     */
    JComponent getComponent();

    /**
     * Checks if this filter is currently enabled.
     *
     * @return true if it should be included in the search
     */
    boolean isEnabled();

    /**
     * Checks if the panel has valid input for search.
     *
     * @return Whether the panel has valid input for search.
     */
    boolean isValid();
    
    /**
     * Get the last error recorded during the call to isValid
     * 
     * @return Description of why the filter is invalid
     */
    String getLastError();

    /**
     * Gets predicate expression to include in the SQL filter expression
     *
     * @return SQL expression that evaluates to a boolean true if the filter
     *         matches the file, otherwise false
     *
     * @throws FilterValidationException with a message if the filter is in an
     *                                   invalid state
     */
    String getPredicate() throws FilterValidationException;

    /**
     * Add an action listener to the fields of this panel
     */
    void addActionListener(ActionListener l);

    /**
     * Adds the property change listener to the panel
     *
     * @param listener the listener to add.
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Thrown if a filter's inputs are invalid
     */
    static class FilterValidationException extends Exception {

        FilterValidationException(String message) {
            super(message);
        }

        FilterValidationException(String message, Exception cause) {
            super(message, cause);
        }
    }
}
