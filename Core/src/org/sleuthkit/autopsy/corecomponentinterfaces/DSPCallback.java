/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.EventQueue;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 *
 * @author raman
 */
public abstract class DSPCallback {
    
    public enum DSP_Result 
            {
                NO_ERRORS, 
                CRITICAL_ERRORS,
                NONCRITICAL_ERRORS,
    };
    
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
    
    public abstract void doneEDT(DSP_Result result, List<String> errList,  List<Content> newContents);
};
