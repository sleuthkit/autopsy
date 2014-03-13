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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
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
    private static final String GENERATOR_LOGO_PATH_PROP = "GeneratorLogoPath";
    private static final String AGENCY_LOGO_PATH_PROP = "AgencyLogoPath";
    private static final String REPORT_TITLE_PROP = "ReportTitle";
    private static final String REPORT_FOOTER_PROP = "ReportFooter";
    //default settings
    private static final String DEFAULT_GENERATOR_LOGO = "/org/sleuthkit/autopsy/report/images/default_generator_logo.png";
    private static final String DEFAULT_REPORT_TITLE = NbBundle
            .getMessage(ReportBranding.class, "ReportBranding.defaultReportTitle.text");
    private static final String DEFAULT_REPORT_FOOTER = NbBundle
            .getMessage(ReportBranding.class, "ReportBranding.defaultReportFooter.text");
    private String reportsBrandingDir; //dir with extracted reports branding resources
    private static final String MODULE_NAME = ReportBranding.class.getSimpleName();
    private static final Logger logger = Logger.getLogger(ReportBranding.class.getName());

    public ReportBranding() {

        //initialize with extracting of resource files if needed, ensure 1 writer at a time
        synchronized (ReportBranding.class) {

            reportsBrandingDir = PlatformUtil.getUserConfigDirectory() + File.separator + ReportGenerator.REPORTS_DIR + File.separator
                    + "branding";
            File brandingDir = new File(reportsBrandingDir);
            if (!brandingDir.exists()) {
                if (!brandingDir.mkdirs()) {
                    logger.log(Level.SEVERE, "Error creating report branding dir for the case, will use defaults");
                    //TODO use defaults
                }
            }
            getGeneratorLogoPath();
            getAgencyLogoPath();
            getReportTitle();
        }
    }

    public String getReportsBrandingDir() {
        return reportsBrandingDir;
    }

    @Override
    public String getGeneratorLogoPath() {
        String curPath = null;
        try {
            curPath = ModuleSettings.getConfigSetting(MODULE_NAME, GENERATOR_LOGO_PATH_PROP);
            if (curPath == null || (!curPath.isEmpty() && !new File(curPath).canRead() ) ) {
                //use default
                logger.log(Level.INFO, "Using default report branding for generator logo");
                curPath = reportsBrandingDir + File.separator + "logo.png";
                InputStream in = getClass().getResourceAsStream(DEFAULT_GENERATOR_LOGO);
                OutputStream output = new FileOutputStream(new File(curPath));
                FileUtil.copy(in, output);
                ModuleSettings.setConfigSetting(MODULE_NAME, GENERATOR_LOGO_PATH_PROP, curPath);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting report branding resources for generator logo", e);
        }

        return curPath;
    }

    @Override
    public void setGeneratorLogoPath(String path) {
        ModuleSettings.setConfigSetting(MODULE_NAME, GENERATOR_LOGO_PATH_PROP, path);
    }

    @Override
    public String getAgencyLogoPath() {
        String curPath = null;

        curPath = ModuleSettings.getConfigSetting(MODULE_NAME, AGENCY_LOGO_PATH_PROP);
        //if has been set, validate it's correct, if not set, return null
        if (curPath != null && new File(curPath).canRead() == false) {
            //use default
            logger.log(Level.INFO, "Custom report branding for agency logo is not valid: " + curPath);
            curPath = null;
        }

        return curPath;
    }

    @Override
    public void setAgencyLogoPath(String path) {
        ModuleSettings.setConfigSetting(MODULE_NAME, AGENCY_LOGO_PATH_PROP, path);
    }

    @Override
    public String getReportTitle() {
        String curTitle = null;

        curTitle = ModuleSettings.getConfigSetting(MODULE_NAME, REPORT_TITLE_PROP);
        if (curTitle == null || curTitle.isEmpty()) {
            //use default
            logger.log(Level.INFO, "Using default report branding for report title");
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
            logger.log(Level.INFO, "Using default report branding for report footer");
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
