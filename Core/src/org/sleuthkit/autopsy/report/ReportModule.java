 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
 * Interface extended by TableReportModule and GeneralReportModule.
 * Contains vital report information to be used by every report.
 */
public interface ReportModule {

    /**
     * Returns a basic string name for the report. What is 'officially' titled.
     *
     * @return the report name
     */
    public String getName();

    /**
     * Returns a one line user friendly description of the type of report this
     * module generates
     * @return user-friendly report description
     */
    public String getDescription();

    /**
     * Calls to the report module to execute a method to get the extension that
     * is used for the report
     *
     * @return String the extension the file will be saved as
     *
     */
    public String getExtension();
    
    /**
     * Returns the path to the main (or only) file for the report.
     * 
     * @return String path to the report file
     */
    public String getFilePath();
}