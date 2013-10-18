/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.EventQueue;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 * Abstract class for a callback
 */
public abstract class DSPCallback {
    
    public enum DSP_Result 
            {
                NO_ERRORS, 
                CRITICAL_ERRORS,
                NONCRITICAL_ERRORS,
    };
    
    /*
     * Invoke the caller supplied callback function on the EDT thread
     */
    public void done(DSP_Result result, List<String> errList,  List<Content> newContents)
    {
        
        final DSP_Result resultf = result;
        final List<String> errListf = errList;
        final List<Content> newContentsf = newContents;
                
            // Invoke doneEDT() that runs on the EDT .
            EventQueue.invokeLater(new Runnable() {
               @Override
               public void run() {
                   doneEDT(resultf, errListf, newContentsf );
                   
               }
           }); 
    }
    
    /*
     * calling code overrides to provide its own calllback 
     */
    public abstract void doneEDT(DSP_Result result, List<String> errList,  List<Content> newContents);
};
