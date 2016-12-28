 /*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Manages settings configured report branding and their defaults.
 *
 * If configured branding is not present on the machine, uses defaults.
 *
 * Uses module settings property files to store customizations.
 */
public final class ReportBranding implements ReportBrandingProviderI {

    //property names
    public static final String AGENCY_LOGO_PATH_PROP = "AgencyLogoPath"; //NON-NLS
    private static final String REPORT_TITLE_PROP = "ReportTitle"; //NON-NLS
    private static final String REPORT_FOOTER_PROP = "ReportFooter"; //NON-NLS
    //default settings
    private static final String DEFAULT_GENERATOR_LOGO = "/org/sleuthkit/autopsy/report/images/default_generator_logo.png"; //NON-NLS
    private static final String DEFAULT_REPORT_TITLE = NbBundle
            .getMessage(ReportBranding.class, "ReportBranding.defaultReportTitle.text");
    private static final String DEFAULT_REPORT_FOOTER = NbBundle
            .getMessage(ReportBranding.class, "ReportBranding.defaultReportFooter.text");
    private String reportsBrandingDir; //dir with extracted reports branding resources
    public static final String MODULE_NAME = ReportBranding.class.getSimpleName();
    private static final Logger logger = Logger.getLogger(ReportBranding.class.getName());

    // this is static so that it can be set by another object
    // before the report is actually made.  Entire class should
    // probably become singleton. Is set to null until setPath
    // is called to specify something other than default.
    private static String generatorLogoPath = null;

    private String defaultGeneratorLogoPath;

    public ReportBranding() {

        //initialize with extracting of resource files if needed, ensure 1 writer at a time
        synchronized (ReportBranding.class) {

            reportsBrandingDir = PlatformUtil.getUserConfigDirectory() + File.separator + ReportGenerator.REPORTS_DIR + File.separator
                    + "branding"; //NON-NLS
            File brandingDir = new File(reportsBrandingDir);
            if (!brandingDir.exists()) {
                if (!brandingDir.mkdirs()) {
                    logger.log(Level.SEVERE, "Error creating report branding dir for the case, will use defaults"); //NON-NLS
                    //TODO use defaults
                }
            }
            extractDefaultGeneratorLogo();
            getAgencyLogoPath();
            getReportTitle();
        }
    }

    public String getReportsBrandingDir() {
        return reportsBrandingDir;
    }

    /**
     * extract default logo from JAR file to local file.
     */
    private void extractDefaultGeneratorLogo() {
        try {
            PlatformUtil.extractResourceToUserConfigDir(getClass(), DEFAULT_GENERATOR_LOGO, true);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error extracting report branding resource for generator logo ", ex); //NON-NLS
        }
        defaultGeneratorLogoPath = PlatformUtil.getUserConfigDirectory() + File.separator + DEFAULT_GENERATOR_LOGO;
    }

    @Override
    public String getGeneratorLogoPath() {
        // if no one called to change the path, use default
        if (generatorLogoPath == null) {
            generatorLogoPath = defaultGeneratorLogoPath;
        }

        return generatorLogoPath;
    }

    @Override
    public void setGeneratorLogoPath(String path) {
        generatorLogoPath = path;
    }

    @Override
    public String getAgencyLogoPath() {
        String curPath = null;

        /*
         * The agency logo code uses these properties to persist changes in the
         * logo (within the same process). This is different from the generator
         * logo that uses a static variable.
         */
        curPath = ModuleSettings.getConfigSetting(MODULE_NAME, AGENCY_LOGO_PATH_PROP);
        //if has been set, validate it's correct, if not set, return null
        if (curPath != null && new File(curPath).canRead() == false) {
            //use default
            logger.log(Level.INFO, "Custom report branding for agency logo is not valid: " + curPath); //NON-NLS
            curPath = null;
        }

        return curPath;
    }

    @Override
    public void setAgencyLogoPath(String path) {
        // Use properties to persist the logo to use.
        // Should use static variable instead
        ModuleSettings.setConfigSetting(MODULE_NAME, AGENCY_LOGO_PATH_PROP, path);
    }

    @Override
    public String getReportTitle() {
        String curTitle = null;

        curTitle = ModuleSettings.getConfigSetting(MODULE_NAME, REPORT_TITLE_PROP);
        if (curTitle == null || curTitle.isEmpty()) {
            //use default
            logger.log(Level.INFO, "Using default report branding for report title"); //NON-NLS
            curTitle = DEFAULT_REPORT_TITLE;
            ModuleSettings.setConfigSetting(MODULE_NAME, REPORT_TITLE_PROP, curTitle);
        }

        return curTitle;
    }

    @Override
    public void setReportTitle(String title) {
        ModuleSettings.setConfigSetting(MODULE_NAME, REPORT_TITLE_PROP, title);
    }

    @Override
    public String getReportFooter() {
        String curFooter = null;

        curFooter = ModuleSettings.getConfigSetting(MODULE_NAME, REPORT_FOOTER_PROP);
        if (curFooter == null) {
            //use default
            logger.log(Level.INFO, "Using default report branding for report footer"); //NON-NLS
            curFooter = DEFAULT_REPORT_FOOTER;
            ModuleSettings.setConfigSetting(MODULE_NAME, REPORT_FOOTER_PROP, curFooter);
        }

        return curFooter;
    }

    @Override
    public void setReportFooter(String footer) {
        ModuleSettings.setConfigSetting(MODULE_NAME, REPORT_FOOTER_PROP, footer);
    }
}
