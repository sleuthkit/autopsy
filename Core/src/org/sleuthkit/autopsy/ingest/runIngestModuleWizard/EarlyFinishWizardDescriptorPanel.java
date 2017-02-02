/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import org.openide.WizardDescriptor;

/**
 * Abstract class for exten
 * 
 */
abstract class ShortCircuitableWizardPanel implements WizardDescriptor.Panel<WizardDescriptor> {
    
    boolean shouldCheckForNext(){
        return true;
    }
}
