 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

import java.sql.SQLException;
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
           catch(SQLException e){
               return false;
           }
           catch(Error e){
               return false;
           }
          
       }
      
}
