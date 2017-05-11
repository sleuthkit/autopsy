/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import org.openide.WizardDescriptor;

/**
 * An abstract class providing a methods which can be checked by the iterator
 * containing panels of this type. So that Wizards containing these panels can
 * skip panels, but still call necessary methods of those panels.
 */
public abstract class ShortcutWizardDescriptorPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    /**
     * Whether or not this panel under the correct conditions can enable the
     * skipping of the panel after it.
     *
     * @return true or false
     */
    public boolean panelEnablesSkipping() {
        /*
         * This method should be overriden by any panel that might want to
         * enable the iterator to skip the panel that comes after it.
         */
        return false;
    }

    /**
     * Whether or not the panel immediately following this one should be skipped
     *
     * @return true or false
     */
    public boolean skipNextPanel() {
        /*
         * This method should be overriden by any panel that might want to
         * enable the iterator to skip the panel that comes after it.
         */
        return false;
    }

    /**
     * Provides a method which will allow code to be executed in a panel you
     * plan to skip
     */
    public void processThisPanelBeforeSkipped() {
        /*
         * If you need to perform some actions of this panel before it is
         * skipped override this method to have it call the necessary code.
         */
    }
}
