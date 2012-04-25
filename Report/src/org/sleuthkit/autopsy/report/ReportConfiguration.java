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

/**
* Configures which parts of report were requested
* e.g. based on user input
* Some specialized reporting modules may choose not to generate all
requested sections
* and some modules may generate additional, specialized sections
*
*/
class ReportConfiguration {

       //setters for generally supported report parts
       public void setGenWebHistory();
       public void setGenWebCookie();
       public void setGenDevices();

       //getters for generally supported report parts
       public void getGenWebHistory();
       public void getGenWebCookie();
       public void getGenDevices();
}
