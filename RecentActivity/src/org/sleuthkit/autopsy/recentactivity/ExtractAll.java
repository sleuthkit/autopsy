/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;

/**
 *
 * @author Alex
 */
public class ExtractAll {
    
       void ExtractAll(){
            
        }
       
       public boolean extractToBlackboard(IngestImageWorkerController controller, int image){
           controller.switchToDeterminate(3);
           try{
               // Will make registry entries later, comment out for DEMO ONLY
               // ExtractRegistry eree = new ExtractRegistry();
                //eree.getregistryfiles();
               controller.switchToDeterminate(3);
               
                Firefox ffre = new Firefox();
                ffre.getffdb(image);  
                controller.progress(1);
                if (controller.isCancelled())
                    return true;
                
                Chrome chre = new Chrome();
                chre.getchdb(image);
                controller.progress(2);
                if (controller.isCancelled())
                    return true;
                
                ExtractIE eere = new ExtractIE(image);
                eere.parsePascoResults();
                controller.progress(3);
                if (controller.isCancelled())
                    return true;
                //Find a way to put these results into BB
               
                return true;
           }
           catch(Error e){
               return false;
           }
          
       }
      
}
