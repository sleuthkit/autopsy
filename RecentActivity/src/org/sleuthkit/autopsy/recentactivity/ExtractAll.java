/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.recentactivity;

import java.util.List;
import org.sleuthkit.autopsy.ingest.IngestImageWorkerController;

/**
 *
 * @author Alex
 */
public class ExtractAll {
    
       void ExtractAll(){
            
        }
       

       public boolean extractToBlackboard(IngestImageWorkerController controller, List<String> imgIds){
           controller.switchToDeterminate(3);
           try{
               // Will make registry entries later, comment out for DEMO ONLY
               controller.switchToDeterminate(4);
               controller.progress(0);
                ExtractRegistry eree = new ExtractRegistry();
                eree.getregistryfiles(imgIds, controller);
                controller.progress(1);
                if (controller.isCancelled())
                    return true;
               
                Firefox ffre = new Firefox();
                ffre.getffdb(imgIds, controller);  
                controller.progress(2);
                if (controller.isCancelled())
                    return true;
                
                Chrome chre = new Chrome();
                chre.getchdb(imgIds, controller);
                controller.progress(3);
                if (controller.isCancelled())
                    return true;
                
                ExtractIE eere = new ExtractIE(imgIds, controller);
                eere.parsePascoResults();
                controller.progress(4);
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
