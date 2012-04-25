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

/**
* Configures which parts of report were requested
* e.g. based on user input
* Some specialized reporting modules may choose not to generate all
requested sections
* and some modules may generate additional, specialized sections
*
*/
class ReportConfiguration {

       //supported report artifact variables -- later maybe make this a dynamic pull and have a generic set/get method
       boolean GenWebHistory;
       boolean GenWebCookie;
       boolean GenWebBookmark;
       boolean GenWebDownload;
       boolean GenInfo;
       boolean GenDevices;
       boolean GenInstalledProg;
       boolean GenKeywordHit;
       boolean GenHashhit;
       boolean GenRecentObject;
    
    ReportConfiguration(){
       
    };
       //setters for generally supported report parts
       public void setGenWebHistory(boolean value){
           GenWebHistory = value;
       };
       public void setGenWebCookie(boolean value){
           GenWebCookie = value;
       };
       public void setGenWebBookmark(boolean value){
           GenWebBookmark = value;
       };
       public void setGenWebDownload(boolean value){
           GenWebDownload = value;
       };
       public void setGenInfo(boolean value){
           GenInfo = value;
       };
       public void setGenDevices(boolean value){
           GenDevices = value;
       };
       public void setGenInstalledProg(boolean value){
           GenInstalledProg = value;
       };
       public void setGenKeywordHit(boolean value){
           GenKeywordHit = value;
       };
       public void setGenHashhit(boolean value){
           GenHashhit = value;
       };
       public void setGenRecentObject(boolean value){
           GenRecentObject = value;
       };
       
       //getters for generally supported report parts
       public boolean getGenWebHistory(){ return GenWebHistory;}
       public boolean getGenWebCookie(){ return GenWebCookie;}
       public boolean getGenWebBookmark(){ return GenWebBookmark;}
       public boolean getGenWebDownload(){ return GenWebDownload;}
       public boolean getGenInfo(){ return GenInfo;}
       public boolean getGenDevices(){ return GenDevices;}
       public boolean getGenInstalledProg(){ return GenInstalledProg;}
       public boolean getGenKeywordHit(){ return GenKeywordHit;}
       public boolean getGenHashhit(){ return GenHashhit;}
       public boolean getGenRecentObject(){ return GenRecentObject;}
}
