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
package org.sleuthkit.autopsy.guicomponentutils;

import javax.swing.Icon;
import org.sleuthkit.autopsy.guiutils.CheckBoxJList;

/**
 * An abstract implementation of CheckBoxJList.CheckboxListItem so that
 * implementing classes have default implementation.
 */
public abstract class AbstractCheckboxListItem implements CheckBoxJList.CheckboxListItem {

    private boolean checked = false;

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public boolean hasIcon() {
        return false;
    }

    @Override
    public Icon getIcon() {
        return null;
    }
}
