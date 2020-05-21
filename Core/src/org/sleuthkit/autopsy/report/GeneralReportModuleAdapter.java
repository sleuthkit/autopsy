/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
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

import javax.swing.JPanel;

/**
 * An adapter that provides no-op implementations of various GeneralReportModule
 * methods.
 */
public abstract class GeneralReportModuleAdapter implements GeneralReportModule {

    @Override
    public abstract String getName();

    @Override
    public abstract String getDescription();

    @Override
    public String getRelativeFilePath() {
        return null;
    }

    @Override
    @Deprecated
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null;
    }
}
