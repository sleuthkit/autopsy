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
 * interface every reporting module should implement
 */
public interface ReportModule {

    /**
     * Generates a report on the current case Reporting module should traverse
     * the blackboard, extract needed information as specified in the config and
     * generate a report file
     *
     * @param config specifying parts that should be generated
     * @return absolute file path to the report generated
     * @throws ReportModuleException thrown when report generation failed
     */
    public String generateReport(ReportConfiguration config) throws ReportModuleException;

    /**
     * This saves a copy of the report (current one) to another place specified
     * by the user. Takes the input of where the path needs to be saved, include
     * filename and extention.
     * 
     * @param path file path where to save a copy of the report
     * @throws ReportModuleException thrown if report saving failed
     */
    public void save(String path) throws ReportModuleException;

    /**
     * Returns a short description of report type/file format this module
     * generates for instance, "XML", "Excel"
     *
     * @return the report type short description
     */
    public String getReportType();

    /**
     * Returns a basic string name for the report. What is 'officially' titled.
     *
     * @return the report name
     */
    public String getName();

    /**
     * Returns the report configuration object that was created
     *
     * @return the report configuration for this report
     */
    public ReportConfiguration GetReportConfiguration();

    /**
     * Returns a one line user friendly description of the type of report this
     * module generates
     * @return user-friendly report description
     */
    public String getDescription();

    /**
     * Calls to the report module to execute a method to display the report that
     * was generated.
     *
     * @param path the path to the file
     *
     */
    public void getPreview(String path);

    /**
     * Calls to the report module to execute a method to get the extension that
     * is used for the report
     *
     * @return String the extension the file will be saved as
     *
     */
    public String getExtension();
}