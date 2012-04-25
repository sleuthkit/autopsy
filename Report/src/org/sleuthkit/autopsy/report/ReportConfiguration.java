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

package org.sleuthkit.autopsy.report;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
* Configures which parts of report were requested
* e.g. based on user input
* Some specialized reporting modules may choose not to generate all
requested sections
* and some modules may generate additional, specialized sections
*
*/
class ReportConfiguration {

       //base data structure
      Map<BlackboardArtifact.ARTIFACT_TYPE,Boolean> config = new EnumMap<BlackboardArtifact.ARTIFACT_TYPE,Boolean>(BlackboardArtifact.ARTIFACT_TYPE.class);
      private final Logger logger = Logger.getLogger(this.getClass().getName());
    
    ReportConfiguration(){
          //clear the config just incase before we get the list from the db again
           config.clear();
           //now lets get the list from the tsk and current case
          Case currentCase = Case.getCurrentCase(); // get the most updated case
          SleuthkitCase skCase = currentCase.getSleuthkitCase();
          try{
               ArrayList<BlackboardArtifact.ARTIFACT_TYPE> arttypes = skCase.getBlackboardArtifactTypes();
               for(BlackboardArtifact.ARTIFACT_TYPE type : arttypes)
               {
                   config.put(type, Boolean.FALSE);
               }
         
          }
          catch(Exception ex)
          {
                logger.log(Level.WARNING, "Error while trying to retrieve list of artifact types from the TSK case .", ex);
          }
       
    };
       //setters for generally supported report parts
       public void setGenArtifactType(BlackboardArtifact.ARTIFACT_TYPE type, Boolean value){
           if(config.containsKey(type))
           {
               config.put(type, value);
           }
       };
       
       
       //getters for generally supported report parts
       public boolean getGenArtifactType(BlackboardArtifact.ARTIFACT_TYPE type){ 
           boolean value = false; 
           if(config.containsKey(type))
           {
               value = config.get(type);
           }
           
           return value;
       
       }   
}
