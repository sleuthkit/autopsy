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

import java.beans.PropertyChangeListener;
import javax.swing.JComponent;

/**
 * Implements common functionality of file search filters
 *
 * @param <T> Type of component to display in file search panel
 *
 * @author pmartel
 */
abstract class AbstractFileSearchFilter<T extends JComponent> implements FileSearchFilter {

    final private T component;
    private String lastErrorMessage;
    
    AbstractFileSearchFilter(T component) {
        this.component = component;
        this.lastErrorMessage = "";
    }
    
    void setLastError(String mes){
        lastErrorMessage = mes;
    }
    
    @Override
    public String getLastError(){
        return this.lastErrorMessage;
    }

    @Override
    public T getComponent() {
        return this.component;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.getComponent().addPropertyChangeListener(listener);
    }
}
