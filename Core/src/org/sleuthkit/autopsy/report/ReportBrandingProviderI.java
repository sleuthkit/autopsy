 /*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
 * Interface to implement by reports to add on custom branding, logos, etc
 */
interface ReportBrandingProviderI {

    /**
     * Get the generator logo path on the local disk (previously set or
     * default), or NULL if unused (Note, this is optional)
     *
     * @return the generator logo path, or null if not provided or error
     *         occurred and path is not valid
     */
    public String getGeneratorLogoPath();

    /**
     * Sets custom generator logo path
     *
     * @param path path to set, use empty string to disable
     */
    public void setGeneratorLogoPath(String path);

    /**
     * Get the agency logo path on the local disk (previously set or default),
     * or NULL if unused (Note, this is optional)
     *
     * @return the agency logo path, or null if not provided or error occurred
     *         and path is not valid
     */
    public String getAgencyLogoPath();

    /**
     * Sets custom agency logo path
     *
     * @param path path to set, use empty string to disable
     */
    public void setAgencyLogoPath(String path);

    /**
     * Get the report title (previously set or default)
     *
     * @return the report title
     */
    public String getReportTitle();

    /**
     * Sets custom report title
     *
     * @param title title to set
     */
    public void setReportTitle(String title);

    /**
     * Get the report footer (previously set or default)
     *
     * @return the report footer
     */
    public String getReportFooter();

    /**
     * Sets custom report footer
     *
     * @param footer footer to set
     */
    public void setReportFooter(String footer);
}
