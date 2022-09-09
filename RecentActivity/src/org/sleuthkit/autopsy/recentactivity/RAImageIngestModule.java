/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.ingest.IngestModule.ProcessResult;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Recent activity image ingest module
 */
public final class RAImageIngestModule implements DataSourceIngestModule {

    private static final String RECENT_ACTIVITY_FOLDER = "RecentActivity";
    private static final Logger logger = Logger.getLogger(RAImageIngestModule.class.getName());
    private final List<Extract> extractors = new ArrayList<>();
    private final List<Extract> browserExtractors = new ArrayList<>();
    private final IngestServices services = IngestServices.getInstance();
    private IngestJobContext context;
    protected SleuthkitCase tskCase;

    RAImageIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        tskCase = Case.getCurrentCase().getSleuthkitCase();

        Extract iexplore = new ExtractIE(context);
        Extract edge = new ExtractEdge(context);
        Extract registry = new ExtractRegistry(context);
        Extract recentDocuments = new RecentDocumentsByLnk(context);
        Extract chrome = new Chromium(context);
        Extract firefox = new Firefox(context);
        Extract SEUQA = new SearchEngineURLQueryAnalyzer(context);
        Extract osExtract = new ExtractOs(context);
        Extract dataSourceAnalyzer = new DataSourceUsageAnalyzer(context);
        Extract safari = new ExtractSafari(context);
        Extract zoneInfo = new ExtractZoneIdentifier(context);
        Extract recycleBin = new ExtractRecycleBin(context);
        Extract sru = new ExtractSru(context);
        Extract prefetch = new ExtractPrefetch(context);
        Extract webAccountType = new ExtractWebAccountType(context);
        Extract messageDomainType = new DomainCategoryRunner(context);
        Extract jumpList = new ExtractJumpLists(context);

        extractors.add(recycleBin);
        extractors.add(jumpList);
        extractors.add(recentDocuments);
        extractors.add(registry); //  needs to run before the DataSourceUsageAnalyzer
        extractors.add(osExtract); // this needs to run before the DataSourceUsageAnalyzer
        extractors.add(dataSourceAnalyzer); //this needs to run after ExtractRegistry and ExtractOs
        extractors.add(chrome);
        extractors.add(firefox);
        extractors.add(iexplore);
        extractors.add(edge);
        extractors.add(safari);
        extractors.add(SEUQA); // this needs to run after the web browser modules
        extractors.add(webAccountType); // this needs to run after the web browser modules
        extractors.add(zoneInfo); // this needs to run after the web browser modules
        extractors.add(sru);
        extractors.add(prefetch);
        extractors.add(messageDomainType);

        browserExtractors.add(chrome);
        browserExtractors.add(firefox);
        browserExtractors.add(iexplore);
        browserExtractors.add(edge);
        browserExtractors.add(safari);

        for (Extract extractor : extractors) {
            extractor.startUp();
        }
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, RecentActivityExtracterModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "RAImageIngestModule.process.started",
                        dataSource.getName())));

        progressBar.switchToDeterminate(extractors.size());

        ArrayList<String> errors = new ArrayList<>();

        for (int i = 0; i < extractors.size(); i++) {
            Extract extracter = extractors.get(i);
            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, "Recent Activity has been canceled, quitting before {0}", extracter.getDisplayName()); //NON-NLS
                break;
            }

            progressBar.progress(extracter.getDisplayName(), i);

            try {
                extracter.process(dataSource, progressBar);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred in " + extracter.getDisplayName(), ex); //NON-NLS
                errors.add(NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.errModErrs", RecentActivityExtracterModuleFactory.getModuleName()));
            }
            progressBar.progress(i + 1);
            errors.addAll(extracter.getErrorMessages());
        }

        // create the final message for inbox
        StringBuilder errorMessage = new StringBuilder();
        String errorMsgSubject;
        MessageType msgLevel = MessageType.INFO;
        if (errors.isEmpty() == false) {
            msgLevel = MessageType.ERROR;
            errorMessage.append(
                    NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.errMsg.errsEncountered"));
            for (String msg : errors) {
                errorMessage.append("<li>").append(msg).append("</li>\n"); //NON-NLS
            }
            errorMessage.append("</ul>\n"); //NON-NLS

            if (errors.size() == 1) {
                errorMsgSubject = NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.errMsgSub.oneErr");
            } else {
                errorMsgSubject = NbBundle.getMessage(this.getClass(),
                        "RAImageIngestModule.process.errMsgSub.nErrs", errors.size());
            }
        } else {
            errorMessage.append(NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.errMsg.noErrs"));
            errorMsgSubject = NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.errMsgSub.noErrs");
        }
        final IngestMessage msg = IngestMessage.createMessage(msgLevel, RecentActivityExtracterModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "RAImageIngestModule.process.ingestMsg.finished",
                        dataSource.getName(), errorMsgSubject),
                errorMessage.toString());
        services.postMessage(msg);

        StringBuilder historyMsg = new StringBuilder();
        historyMsg.append(
                NbBundle.getMessage(this.getClass(), "RAImageIngestModule.process.histMsg.title", dataSource.getName()));
        for (Extract module : browserExtractors) {
            historyMsg.append("<li>").append(module.getDisplayName()); //NON-NLS
            historyMsg.append(": ").append((module.foundData()) ? NbBundle
                    .getMessage(this.getClass(), "RAImageIngestModule.process.histMsg.found") : NbBundle
                    .getMessage(this.getClass(), "RAImageIngestModule.process.histMsg.notFnd"));
            historyMsg.append("</li>"); //NON-NLS
        }
        historyMsg.append("</ul>"); //NON-NLS
        final IngestMessage inboxMsg = IngestMessage.createMessage(MessageType.INFO, RecentActivityExtracterModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "RAImageIngestModule.process.ingestMsg.results",
                        dataSource.getName()),
                historyMsg.toString());
        services.postMessage(inboxMsg);

        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        for (int i = 0; i < extractors.size(); i++) {
            Extract extracter = extractors.get(i);
            try {
                extracter.shutDown();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Exception occurred when completing " + extracter.getDisplayName(), ex); //NON-NLS
            }
        }
    }

    /**
     * Makes a path of the format
     * [basePath]/[RECENT_ACTIVITY_FOLDER]/[module]_[ingest job id] if it does
     * not already exist and returns the created folder.
     *
     * @param basePath    The base path (a case-related folder like temp or
     *                    output).
     * @param module      The module name to include in the folder name.
     * @param ingestJobId The id of the ingest job.
     *
     * @return The path to the folder.
     */
    private static String getAndMakeRAPath(String basePath, String module, long ingestJobId) {
        String moduleFolder = String.format("%s_%d", module, ingestJobId);
        Path tmpPath = Paths.get(basePath, RECENT_ACTIVITY_FOLDER, moduleFolder);
        File dir = tmpPath.toFile();
        if (dir.exists() == false) {
            dir.mkdirs();
        }
        return tmpPath.toString();
    }

    /**
     * Get the temp path for a specific sub-module in recent activity. Will
     * create the dir if it doesn't exist.
     *
     * @param a_case Case that directory is for
     * @param mod    Module name that will be used for a sub folder in the temp
     *               folder to prevent name collisions
     *
     * @return Path to directory
     */
    static String getRATempPath(Case a_case, String mod, long ingestJobId) {
        return getAndMakeRAPath(a_case.getTempDirectory(), mod, ingestJobId);
    }

    /**
     * Get the output path for a specific sub-module in recent activity. Will
     * create the dir if it doesn't exist.
     *
     * @param a_case Case that directory is for
     * @param mod    Module name that will be used for a sub folder in the temp
     *               folder to prevent name collisions
     *
     * @return Path to directory
     */
    static String getRAOutputPath(Case a_case, String mod, long ingestJobId) {
        return getAndMakeRAPath(a_case.getModuleDirectory(), mod, ingestJobId);
    }

    /**
     * Get relative path for module output folder.
     *
     * @throws NoCurrentCaseException if there is no open case.
     * @return the relative path of the module output folder
     */
    static String getRelModuleOutputPath(Case autCase, String mod, long ingestJobId) {
        return Paths.get(getAndMakeRAPath(autCase.getModuleOutputDirectoryRelativePath(), mod, ingestJobId))
                .normalize()
                .toString();
    }
}
