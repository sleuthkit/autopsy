 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> autopsy <dot> org
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
import java.util.HashMap;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 * This class is the 'default' way to get artifacts/attributes from the blackboard using a reportconfiguration object. 
 */
public class ReportGen {
  
  ReportGen(){
      
  }
  
  
  public HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> generateReport(ReportConfiguration config){
      HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>> Results = new HashMap<BlackboardArtifact,ArrayList<BlackboardAttribute>>();
      report bbreport = new report();
             if(config.getGenInfo()){Results.putAll(bbreport.getGenInfo());}
             if(config.getGenWebBookmark()){Results.putAll(bbreport.getWebBookmark());}
             if(config.getGenWebCookie()){Results.putAll(bbreport.getWebCookie());}
             if(config.getGenWebHistory()){Results.putAll(bbreport.getWebHistory());}
             if(config.getGenWebDownload()){Results.putAll(bbreport.getWebDownload());}
             if(config.getGenRecentObject()){Results.putAll(bbreport.getRecentObject());}
            // if(reportlist.contains(7)){Results.putAll(bbreport.getGenInfo());}
             if(config.getGenInstalledProg()){Results.putAll(bbreport.getInstalledProg());}
             if(config.getGenKeywordHit()){Results.putAll(bbreport.getKeywordHit());}
             if(config.getGenHashhit()){Results.putAll(bbreport.getHashHit());}
              if(config.getGenDevices()){Results.putAll(bbreport.getDevices());}
      return Results;
  }
}
