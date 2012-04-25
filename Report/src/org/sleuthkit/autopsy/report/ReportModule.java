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

//interface every reporting module should implement
public interface ReportModule {

       /**
       * Generates a report on the current case
       * Reporting module should traverse the blackboard, extract needed
       *  information as specified in the config
       * and generate a report file
       *
       * @param config specifiying parts that should be generated
       * @return absolute file path to the report generated
       * @throws ReportModuleException if report generation failed
       */
       public String generateReport(ReportConfiguration config) throws ReportModuleException;


       /**
       * save already generated report to the user specified location ???
       * or should this be part of generateReport() ???
       */
       public void save() throws ReportModuleException;

       /**
       * Returns a short description of report type/file format this module generates
       * for instance, "XML", "Excel"
       * @return
       */
       public String getReportType();


       /**
       * Returns a one line human readable description of the type of report
this module generates
       */
       public String getReportTypeDescription();




}