/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponentinterfaces;

/*
 * An GUI agnostic DSPProgressMonitor interface for DataSorceProcesssors to 
 * indicate progress.
 * It models after a JProgressbar though could use any underlying implementation
 */
public interface DSPProgressMonitor {
 
    void setIndeterminate(boolean indeterminate);
    
    void setProgress(int progress);
    
    void setText(String text);   
}
