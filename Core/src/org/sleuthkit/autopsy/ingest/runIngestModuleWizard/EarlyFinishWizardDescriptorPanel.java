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
 * An abstract class providing a method which can be checked by 
 * the iterator containing panels of this type. So that Wizards containing these
 * panels can enable finish before the last panel.
 */
abstract class EarlyFinishWizardDescriptorPanel implements WizardDescriptor.Panel<WizardDescriptor> {
    
    /**
     * Whether or not this should be treated as the last panel.
     * 
     * @return true or false
     */
    boolean skipRemainingPanels(){
        /*
         * This class should be overriden by any panel that might want to 
         * enable the finish button early for its wizard.
         */
        return false;
    }
}
