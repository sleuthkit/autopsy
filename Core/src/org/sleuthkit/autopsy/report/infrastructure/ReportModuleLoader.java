/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import org.sleuthkit.autopsy.report.GeneralReportModule;
import java.util.ArrayList;
import java.util.List;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.python.JythonModuleLoader;

/**
 * Class used to find and instantiate report module singletons. Provides a
 * single mechanism by which clients that interact with report modules can
 * obtain them.
 */
class ReportModuleLoader {

    static List<GeneralReportModule> getGeneralReportModules() {
        List<GeneralReportModule> generalModules = new ArrayList<>();

        for (GeneralReportModule module : Lookup.getDefault().lookupAll(GeneralReportModule.class)) {
            generalModules.add(module);
        }

        for (GeneralReportModule module : JythonModuleLoader.getGeneralReportModules()) {
            generalModules.add(module);
        }

        return generalModules;
    }

    static List<TableReportModule> getTableReportModules() {
        List<TableReportModule> tableModules = new ArrayList<>();
        for (TableReportModule module : Lookup.getDefault().lookupAll(TableReportModule.class)) {
            tableModules.add(module);
        }
        return tableModules;
    }

    // Initialize the list of ReportModules
    static List<FileReportModule> getFileReportModules() {
        List<FileReportModule> fileModules = new ArrayList<>();
        for (FileReportModule module : Lookup.getDefault().lookupAll(FileReportModule.class)) {
            fileModules.add(module);
        }
        return fileModules;
    }
}
