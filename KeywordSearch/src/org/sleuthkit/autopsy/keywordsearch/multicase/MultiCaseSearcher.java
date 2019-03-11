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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;
import org.sleuthkit.autopsy.keywordsearch.Server;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Performs keyword searches across multiple cases
 */
final class MultiCaseSearcher {

    private static final String CASE_AUTO_INGEST_LOG_NAME = "AUTO_INGEST_LOG.TXT"; //NON-NLS
    private static final String SEARCH_COMPLETE_MESSAGE = "SEARCH_COMPLETE";
    private static final String RESOURCES_LOCK_SUFFIX = "_RESOURCES"; //NON-NLS
    private static final int CASE_DIR_READ_LOCK_TIMEOUT_HOURS = 12; //NON-NLS    
    private static final String SOLR_SERVER_URL_FORMAT_STRING = "http://%s:%s/solr"; //NON-NLS
    private static final String SOLR_CORE_URL_FORMAT_STRING = "http://%s:%s/solr/%s"; //NON-NLS
    private final static String SOLR_METADATA_FILE_NAME = "SolrCore.properties"; //NON-NLS
    private static final String SOLR_CORE_NAME_XPATH = "/SolrCores/Core/CoreName/text()"; //NON-NLS
    private static final String TEXT_INDEX_NAME_XPATH = "/SolrCores/Core/TextIndexPath/text()"; //NON-NLS
    private static final String SOLR_CORE_INSTANCE_PATH_PROPERTY = "instanceDir"; //NON-NLS
    private static final String SOLR_CONFIG_SET_NAME = "AutopsyConfig"; //NON-NLS
    private static final int MAX_RESULTS_PER_CURSOR_MARK = 512;
    private static final String SOLR_DOC_ID_FIELD = Server.Schema.ID.toString(); //NON-NLS
    private static final String SOLR_DOC_CONTENT_STR_FIELD = Server.Schema.CONTENT_STR.toString(); //NON-NLS
    private static final String SOLR_DOC_CHUNK_SIZE_FIELD = Server.Schema.CHUNK_SIZE.toString(); //NON-NLS  
    private static final String SOLR_DOC_ID_PARTS_SEPARATOR = "_";
    private static final Logger logger = Logger.getLogger(MultiCaseSearcher.class.getName());
    private final EventBus eventBus = new EventBus("MultiCaseSearcherEventBus");
    private static final UNCPathUtilities pathUtils = new UNCPathUtilities();
    private volatile boolean searchStopped = true;

    MultiCaseSearcher() {

    }

    static String getSearchCompleteMessage() {
        return SEARCH_COMPLETE_MESSAGE;
    }

    /**
     *
     * Performs keyword searches across multiple cases
     *
     * @param caseNames   The names of the cases to search.
     * @param query             The keyword search query to perform.
     * @param progressIndicator A progrss indicator for the search.
     *
     * @return The search results.
     *
     * @throws MultiCaseSearcherException
     * @throws InterruptedException
     */
    @NbBundle.Messages({
        "MultiCaseSearcher.progressMessage.findingCases=Finding selected cases",
        "MultiCaseSearcher.progressMessage.creatingSolrQuery=Creating search query for Solr server",
        "# {0} - total cases",
        "MultiCaseSearcher.progressMessage.startingCaseSearches=Searching {0} case(s)",
        "# {0} - case name",
        "# {1} - case counter",
        "# {2} - total cases",
        "MultiCaseSearcher.progressMessage.acquiringSharedLockForCase=Acquiring shared lock for \"{0}\" ({1} of {2} case(s))",
        "# {0} - case name",
        "# {1} - case counter",
        "# {2} - total cases",
        "MultiCaseSearcher.progressMessage.loadingSolrCoreForCase=Loading Solr core for \"{0}\" ({1} of {2} case(s))",
        "# {0} - case name",
        "# {1} - case counter",
        "# {2} - total cases",
        "MultiCaseSearcher.progressMessage.openingCaseDbForCase=Opening case database for \"{0}\" ({1} of {2} case(s))",
        "# {0} - case name",
        "# {1} - case counter",
        "# {2} - total cases",
        "MultiCaseSearcher.progressMessage.executingSolrQueryForCase=Getting keyword hits for \"{0}\" ({1} of {2} case(s))",
        "# {0} - case directory path",
        "MultiCaseSearcher.exceptionMessage.failedToGetCaseDirReadlock=Failed to obtain read lock for case directory at {0}",
        "MultiCaseSearcher.exceptionMessage.cancelledMessage=Search cancelled"
    })
    void performKeywordSearch(final Collection<String> caseNames, final SearchQuery query, final ProgressIndicator progressIndicator) {
        progressIndicator.start(Bundle.MultiCaseSearcher_progressMessage_findingCases());
        try {
            searchStopped = false;  //mark the search as started
            final List<MultiCaseMetadata> caseMetadata = getMultiCaseMetadata(caseNames);
            checkForCancellation();
            //eventBus.post("number of cases to search determined");
            progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_creatingSolrQuery());
            final SolrQuery solrQuery = createSolrQuery(query);
            checkForCancellation();
            final int totalCases = caseMetadata.size();
            int caseCounter = 1;
            progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_startingCaseSearches(totalCases));
            int totalSteps = 5;
            progressIndicator.switchToDeterminate(Bundle.MultiCaseSearcher_progressMessage_startingCaseSearches(totalCases), 0, totalCases * totalSteps);
            int caseNumber = 0;
            for (MultiCaseMetadata aCase : caseMetadata) {
                CaseMetadata metadata = aCase.getCaseMetadata();
                String caseName = metadata.getCaseDisplayName();
                SleuthkitCase caseDatabase = null;

                int stepsCompleted = 0;
                progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_acquiringSharedLockForCase(caseName, caseCounter, totalCases), stepsCompleted + caseNumber * totalSteps);
                try (CoordinationService.Lock caseDirReadLock = CoordinationService.getInstance().tryGetSharedLock(CoordinationService.CategoryNode.CASES, aCase.getCaseMetadata().getCaseDirectory(), CASE_DIR_READ_LOCK_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                    if (null == caseDirReadLock) {
                        throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToGetCaseDirReadlock(aCase.getCaseMetadata().getCaseDirectory()));
                    }
                    checkForCancellation();
                    ++stepsCompleted;
                    progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_loadingSolrCoreForCase(caseName, caseCounter, totalCases), stepsCompleted + caseNumber * totalSteps);
                    final HttpSolrServer solrServer = loadSolrCoreForCase(aCase);
                    checkForCancellation();
                    ++stepsCompleted;
                    progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_openingCaseDbForCase(caseName, caseCounter, totalCases), stepsCompleted + caseNumber * totalSteps);
                    caseDatabase = openCase(aCase);
                    checkForCancellation();
                    ++stepsCompleted;
                    progressIndicator.progress(Bundle.MultiCaseSearcher_progressMessage_executingSolrQueryForCase(caseName, caseCounter, totalCases), stepsCompleted + caseNumber * totalSteps);
                    eventBus.post(executeQuery(solrServer, solrQuery, caseDatabase, aCase));
                    ++stepsCompleted;

                    progressIndicator.progress(stepsCompleted + caseNumber * totalSteps);
                    ++caseCounter;
                } catch (CoordinationService.CoordinationServiceException ex) {
                    throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToGetCaseDirReadlock(aCase.getCaseMetadata().getCaseDirectory()), ex);
                } catch (MultiCaseSearcherException exception) {
                    logger.log(Level.INFO, "Exception encountered while performing multi-case keyword search", exception);
                    eventBus.post(exception);
                } finally {
                    if (null != caseDatabase) {
                        closeCase(caseDatabase);
                    }
                }
                caseNumber++;
            }
        } catch (InterruptedException exception) {
            logger.log(Level.INFO, Bundle.MultiCaseSearcher_exceptionMessage_cancelledMessage(), exception);
            eventBus.post(exception);
        } catch (MultiCaseSearcherException exception) {
            logger.log(Level.WARNING, "Exception encountered while performing multi-case keyword search", exception);
            eventBus.post(new InterruptedException("Exception encountered while performing multi-case keyword search"));
            eventBus.post(exception);
        } finally {
            progressIndicator.finish();
            eventBus.post(SEARCH_COMPLETE_MESSAGE);
        }
    }

    /**
     * Gets metadata for the cases associated with one or more with the search
     *
     * @param caseNames The names of the cases to search.
     *
     * @return The metadata for the cases.
     *
     * @throws MultiCaseSearcherException
     * @throws InterruptedException
     */
    private List<MultiCaseMetadata> getMultiCaseMetadata(final Collection<String> caseNames) throws MultiCaseSearcherException, InterruptedException {
        final Map<Path, String> casesToCasePaths = getCaseDirectories(caseNames);
        checkForCancellation();
        final List<MultiCaseMetadata> cases = new ArrayList<>();
        for (Map.Entry<Path, String> entry : casesToCasePaths.entrySet()) {
            final Path caseDirectoryPath = entry.getKey();
            final CaseMetadata caseMetadata = getCaseMetadata(caseDirectoryPath);
            checkForCancellation();
            final TextIndexMetadata textIndexMetadata = getTextIndexMetadata(caseDirectoryPath);
            checkForCancellation();
            cases.add(new MultiCaseMetadata(caseMetadata, textIndexMetadata));
        }
        return cases;
    }

    /**
     * Uses coordination service data to find the case directories of the cases.
     *
     * @param caseNames The names of the cases.
     *
     * @return A mapping of case directory paths to case names,
     *         possibly empty.
     *
     * @throws MultiCaseSearcherException
     * @throws InterruptedException
     */
    @NbBundle.Messages({
        "# {0} - host", "# {1} - port", "MultiCaseSearcher.exceptionMessage.failedToQueryCoordinationServer=Failed to obtain read lock for case directory at {0}:{1}",
        "# {0} - list of cases", "MultiCaseSearcher.exceptionMessage.noCasesFound=No cases found for: {0}"
    })
    private Map<Path, String> getCaseDirectories(final Collection<String> caseNames) throws MultiCaseSearcherException, InterruptedException {
        final Map<Path, String> casePathToCaseMap = new HashMap<>();
        final List<String> caseNodeNames;
        try {
            CoordinationService coordinationService = CoordinationService.getInstance();
            caseNodeNames = coordinationService.getNodeList(CoordinationService.CategoryNode.CASES);
        } catch (CoordinationService.CoordinationServiceException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToQueryCoordinationServer(UserPreferences.getIndexingServerHost(), UserPreferences.getIndexingServerPort()), ex);
        }
        for (String nodeName : caseNodeNames) {
            /*
             * Find the case directory paths by
             * selecting each coordination service case directory lock node path
             * that has the case name in the path.
             */
            checkForCancellation();
            final Path caseDirectoryPath = Paths.get(nodeName);
            boolean contansSlash = caseDirectoryPath.toString().contains("\\") || caseDirectoryPath.toString().contains("//");
            if (!contansSlash) {
                /*
                 * Skip case name lock nodes.
                 */
                continue;
            }
            final String fileName = caseDirectoryPath.getFileName().toString();
            if (fileName.equals(CASE_AUTO_INGEST_LOG_NAME) || fileName.endsWith(RESOURCES_LOCK_SUFFIX)) {
                /*
                 * Skip case auto ingest log and case resource lock nodes.
                 */
                continue;
            }
            for (String aCase : caseNames) {
                checkForCancellation();
                final String normalizedCaseName = aCase.toUpperCase();
                if (fileName.contains(normalizedCaseName)) {
                    logger.log(Level.INFO, "Match found: Case node name {0} contains case name {1}", new Object[]{nodeName, normalizedCaseName});
                    try {
                        Path realCaseDirectoryPath = caseDirectoryPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
                        logger.log(Level.INFO, "Case directory path {0} resolves to real path {1}", new Object[]{caseDirectoryPath, realCaseDirectoryPath});
                        final File caseDirectory = realCaseDirectoryPath.toFile();
                        if (caseDirectory.exists()) {
                            casePathToCaseMap.put(realCaseDirectoryPath, aCase);
                        } else {
                            logger.log(Level.SEVERE, String.format("Case directory %s does NOT exist", caseDirectoryPath));
                        }
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, String.format("Case directory path %s does NOT resolve to a real path", caseDirectoryPath), ex);
                    }
                    break;
                }
            }
        }
        if (casePathToCaseMap.isEmpty()) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_noCasesFound(StringUtils.join(caseNames, ',')));
        }
        return casePathToCaseMap;
    }

    /**
     * Gets the metadata for a case from the case metadata file in a given case
     * directory.
     *
     * @param caseDirectoryPath A case directory path.
     *
     * @return The case metadata.
     *
     * @throws MultiCaseSearcherException
     */
    @NbBundle.Messages({
        "# {0} - case directory", "MultiCaseSearcher.exceptionMessage.failedToFindCaseMetadata=Failed to find case metadata file in {0}",
        "# {0} - case directory", "MultiCaseSearcher.exceptionMessage.failedToParseCaseMetadata=Failed to parse case file metadata in {0}"
    })

    private static CaseMetadata getCaseMetadata(Path caseDirectoryPath) throws MultiCaseSearcherException {
        CaseMetadata caseMetadata = null;
        final File[] caseFiles = caseDirectoryPath.toFile().listFiles();
        for (File file : caseFiles) {
            final String fileName = file.getName().toLowerCase();
            if (fileName.endsWith(CaseMetadata.getFileExtension())) {
                try {
                    return new CaseMetadata(file.toPath());
                } catch (CaseMetadata.CaseMetadataException ex) {
                    throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToParseCaseMetadata(caseDirectoryPath), ex);
                }
            }
        }
        if (null == caseMetadata) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToFindCaseMetadata(caseDirectoryPath));
        }
        return caseMetadata;
    }

    /**
     * Gets the text index metadata from the Solr.properties file in a given
     * case directory.
     *
     * @param caseDirectoryPath A case directory path.
     *
     * @return The text index metadata.
     *
     * @throws MultiCaseSearcherException
     */
    @NbBundle.Messages({
        "# {0} - file name", "# {1} - case directory", "MultiCaseSearcher.exceptionMessage.missingSolrPropertiesFile=Missing {0} file in {1}",
        "# {0} - file name", "# {1} - case directory", "MultiCaseSearcher.exceptionMessage.solrPropertiesFileParseError=Error parsing {0} file in {1}",})
    private static TextIndexMetadata getTextIndexMetadata(Path caseDirectoryPath) throws MultiCaseSearcherException {
        final Path solrMetaDataFilePath = Paths.get(caseDirectoryPath.toString(), SOLR_METADATA_FILE_NAME);
        final File solrMetaDataFile = solrMetaDataFilePath.toFile();
        if (!solrMetaDataFile.exists() || !solrMetaDataFile.canRead()) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_missingSolrPropertiesFile(SOLR_METADATA_FILE_NAME, caseDirectoryPath));
        }
        try {
            final DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document doc = docBuilder.parse(solrMetaDataFile);
            final XPath xPath = XPathFactory.newInstance().newXPath();
            XPathExpression xPathExpr = xPath.compile(SOLR_CORE_NAME_XPATH);
            final String solrCoreName = (String) xPathExpr.evaluate(doc, XPathConstants.STRING);
            xPathExpr = xPath.compile(TEXT_INDEX_NAME_XPATH);
            final String relativeTextIndexPath = (String) xPathExpr.evaluate(doc, XPathConstants.STRING);
            Path textIndexPath = caseDirectoryPath.resolve(relativeTextIndexPath);
            textIndexPath = textIndexPath.getParent(); // Remove "index" path component
            final String textIndexUNCPath = pathUtils.convertPathToUNC(textIndexPath.toString());
            return new TextIndexMetadata(caseDirectoryPath, solrCoreName, textIndexUNCPath);
        } catch (ParserConfigurationException | SAXException | XPathExpressionException | IOException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_solrPropertiesFileParseError(SOLR_METADATA_FILE_NAME, caseDirectoryPath), ex);
        }
    }

    /**
     * Converts a keyword search query into a Solr query.
     *
     * @param searchQuery A keyword search query.
     *
     * @return A Solr query.
     */
    private static SolrQuery createSolrQuery(SearchQuery searchQuery) {
        final SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(searchQuery.getSearchTerm());
        solrQuery.setRows(MAX_RESULTS_PER_CURSOR_MARK);
        /*
         * Note that setting the sort order is necessary for cursor based paging
         * to work.
         */
        solrQuery.setSort(SolrQuery.SortClause.asc(SOLR_DOC_ID_FIELD));
        solrQuery.setFields(SOLR_DOC_ID_FIELD, SOLR_DOC_CHUNK_SIZE_FIELD, SOLR_DOC_CONTENT_STR_FIELD);
        return solrQuery;
    }

    /**
     * Connects to the Solr server and loads the Solr core for a given case.
     *
     * @param aCase
     *
     * @return A Solr server client object that can be used for executing
     *         queries of the specified text index.
     *
     * MultiCaseSearcherException
     *
     * @throws InterruptedException
     */
    @NbBundle.Messages({
        "# {0} - connection info",
        "# {1} - case name",
        "# {2} - case directory",
        "MultiCaseSearcher.exceptionMessage.errorLoadingCore=Error connecting to Solr server and loading core (URL: {0}) for case {1} in {2}"
    })
    private HttpSolrServer loadSolrCoreForCase(MultiCaseMetadata aCase) throws MultiCaseSearcherException, InterruptedException {
        TextIndexMetadata textIndexMetadata = aCase.getTextIndexMetadata();
        Server.IndexingServerProperties indexServer = Server.getMultiUserServerProperties(aCase.getCaseMetadata().getCaseDirectory());
        final String serverURL = String.format(SOLR_SERVER_URL_FORMAT_STRING, indexServer.getHost(), indexServer.getPort());
        try {
            /*
             * Connect to the Solr server.
             */
            final HttpSolrServer solrServer = new HttpSolrServer(serverURL);
            CoreAdminRequest statusRequest = new CoreAdminRequest();
            statusRequest.setCoreName(null);
            statusRequest.setAction(CoreAdminParams.CoreAdminAction.STATUS);
            statusRequest.setIndexInfoNeeded(false);
            checkForCancellation();
            statusRequest.process(solrServer);
            checkForCancellation();

            /*
             * Load the core for the text index if it is not already loaded.
             */
            CoreAdminResponse response = CoreAdminRequest.getStatus(textIndexMetadata.getSolrCoreName(), solrServer);
            if (null == response.getCoreStatus(textIndexMetadata.getSolrCoreName()).get(SOLR_CORE_INSTANCE_PATH_PROPERTY)) {
                CoreAdminRequest.Create loadCoreRequest = new CoreAdminRequest.Create();
                loadCoreRequest.setDataDir(textIndexMetadata.getTextIndexPath());
                loadCoreRequest.setCoreName(textIndexMetadata.getSolrCoreName());
                loadCoreRequest.setConfigSet(SOLR_CONFIG_SET_NAME);
                loadCoreRequest.setIsLoadOnStartup(false);
                loadCoreRequest.setIsTransient(true);
                solrServer.request(loadCoreRequest);
            }

            /*
             * Create a server client object that can be used for executing
             * queries of the specified text index.
             */
            final String coreURL = String.format(SOLR_CORE_URL_FORMAT_STRING, indexServer.getHost(), indexServer.getPort(), textIndexMetadata.getSolrCoreName());
            final HttpSolrServer coreServer = new HttpSolrServer(coreURL);
            return coreServer;

        } catch (SolrServerException | IOException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_errorLoadingCore(serverURL, aCase.getCaseMetadata().getCaseName(), textIndexMetadata.getCaseDirectoryPath()), ex);
        }
    }

    /**
     * Opens a case database.
     *
     * @param caseMetadata
     *
     * @return A case database.
     *
     * @throws MultiCaseSearcherException
     * @throws InterruptedException
     */
    @NbBundle.Messages({
        "# {0} - case_name",
        "MultiCaseSearcher.exceptionMessage.failedToGetCaseDatabaseConnectionInfo=Failed to get case database connection info for case {0}",
        "# {0} - PostgreSQL server host",
        "# {1} - PostgreSQL server port",
        "# {2} - case database name",
        "# {3} - case directory",
        "MultiCaseSearcher.exceptionMessage.errorOpeningCaseDatabase=Error connecting to PostgreSQL server (Host/Port: [{0}:{1}] and opening case database {2} for case at {3}"
    })
    private SleuthkitCase openCase(MultiCaseMetadata aCase) throws MultiCaseSearcherException, InterruptedException {
        CaseDbConnectionInfo dbConnectionInfo;
        try {
            dbConnectionInfo = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_failedToGetCaseDatabaseConnectionInfo(aCase.getCaseMetadata().getCaseName()), ex);
        }
        checkForCancellation();
        final CaseMetadata caseMetadata = aCase.getCaseMetadata();
        try {
            return SleuthkitCase.openCase(caseMetadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), caseMetadata.getCaseDirectory());
        } catch (UserPreferencesException | TskCoreException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_errorOpeningCaseDatabase(dbConnectionInfo.getHost(), dbConnectionInfo.getPort(), caseMetadata.getCaseDatabaseName(), caseMetadata.getCaseDirectory()), ex);
        }
    }

    /**
     * Closes a case database.
     *
     * @param aCase a case database.
     */
    private static void closeCase(SleuthkitCase aCase) {
        aCase.close();
    }

    /**
     * Executes a keyword search searchTerm in the text index of a case.
     *
     * @param solrServer     The Solr server.
     * @param solrQuery      The Solr searchTerm.
     * @param caseDatabase   The case database.
     * @param aCase The case metadata.
     *
     * @return A list of search results, possibly empty.
     *
     * @throws MultiCaseSearcherException
     * @throws InterruptedException
     */
    @NbBundle.Messages({
        "# {0} - query", 
        "# {1} - case_name",
        "MultiCaseSearcher.exceptionMessage.solrQueryError=Failed to execute query \"{0}\" on case {1}"
    })
    private Collection<SearchHit> executeQuery(HttpSolrServer solrServer, SolrQuery solrQuery, SleuthkitCase caseDatabase, MultiCaseMetadata aCase) throws MultiCaseSearcherException, InterruptedException {
        final List<SearchHit> hits = new ArrayList<>();
        final Set<Long> uniqueObjectIds = new HashSet<>();
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        boolean allResultsProcessed = false;
        while (!allResultsProcessed) {
            checkForCancellation();
            solrQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
            QueryResponse response;
            try {
                checkForCancellation();
                response = solrServer.query(solrQuery, SolrRequest.METHOD.POST);
            } catch (SolrServerException ex) {
                throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_solrQueryError(solrQuery.getQuery(), aCase.getCaseMetadata().getCaseName()), ex);
            }
            SolrDocumentList resultDocuments = response.getResults();
            for (SolrDocument resultDoc : resultDocuments) {
                checkForCancellation();
                String solrDocumentId = resultDoc.getFieldValue(SOLR_DOC_ID_FIELD).toString();
                Long solrObjectId = parseSolrObjectId(solrDocumentId);
                if (!uniqueObjectIds.contains(solrObjectId)) {
                    uniqueObjectIds.add(solrObjectId);
                    checkForCancellation();
                    hits.add(processHit(solrObjectId, caseDatabase, aCase));
                }
            }
            checkForCancellation();
            String nextCursorMark = response.getNextCursorMark();
            if (cursorMark.equals(nextCursorMark)) {
                allResultsProcessed = true;
            }
            cursorMark = nextCursorMark;
        }
        return hits;
    }

    /**
     * Parses a Solr document id to get the Solr object id.
     *
     * @param solrDocumentId A Solr document id.
     *
     * @return A Solr object id.
     */
    private static Long parseSolrObjectId(String solrDocumentId) {
        /**
         * A Solr document id is of the form [solr_object_id] for Content object
         * metadata documents and
         * [solr_object_id][SOLR_DOC_ID_PARTS_SEPARATOR][chunk_id] for Content
         * object text chunk documents.
         */
        final String[] solrDocumentIdParts = solrDocumentId.split(SOLR_DOC_ID_PARTS_SEPARATOR);
        if (1 == solrDocumentIdParts.length) {
            return Long.parseLong(solrDocumentId);
        } else {
            return Long.parseLong(solrDocumentIdParts[0]);
        }
    }

    /**
     * Creates a keyword search hit object for a Content object identified by
     * its Solr object id.
     *
     * @param solrObjectId       The Solr object id of a Content object.
     * @param caseDatabase       The case database of the case that has the
     *                           Content.
     * @param caseInfo Metadata about the case that has the content.
     *
     * @return
     *
     * @throws MultiCaseSearcherException
     */
    @NbBundle.Messages({
        "# {0} - Solr document id",
        "# {1} - case database name",
        "# {2} - case directory",
        "MultiCaseSearcher.exceptionMessage.hitProcessingError=Failed to query case database for processing of Solr object id {0} of case {1} in {2}"
    })

    private static SearchHit processHit(Long solrObjectId, SleuthkitCase caseDatabase, MultiCaseMetadata caseInfo) throws MultiCaseSearcherException {
        try {
            final long objectId = getObjectIdForSolrObjectId(solrObjectId, caseDatabase);
            final CaseMetadata caseMetadata = caseInfo.getCaseMetadata();
            final String caseDisplayName = caseMetadata.getCaseDisplayName();
            final String caseDirectoryPath = caseMetadata.getCaseDirectory();
            final Content content = caseDatabase.getContentById(objectId);
            final Content dataSource = content.getDataSource();
            final String dataSourceName = dataSource.getName();
            SearchHit.SourceType sourceType = SearchHit.SourceType.FILE;
            String sourceName = "";
            String sourcePath = "";
            if (content instanceof AbstractFile) {
                AbstractFile sourceFile = (AbstractFile) content;
                sourceName = sourceFile.getName();
                sourcePath = sourceFile.getLocalAbsPath();
                if (null == sourcePath) {
                    sourceType = SearchHit.SourceType.FILE;
                    sourcePath = sourceFile.getUniquePath();
                } else {
                    sourceType = SearchHit.SourceType.LOCAL_FILE;
                    sourceName = sourceFile.getName();
                }
            } else if (content instanceof BlackboardArtifact) {
                BlackboardArtifact sourceArtifact = (BlackboardArtifact) content;
                sourceType = SearchHit.SourceType.ARTIFACT;
                BlackboardArtifact.Type artifactType = caseDatabase.getArtifactType(sourceArtifact.getArtifactTypeName());
                sourceName = artifactType.getDisplayName();
                Content source = sourceArtifact.getParent();
                if (source instanceof AbstractFile) {
                    AbstractFile sourceFile = (AbstractFile) source;
                    sourcePath = sourceFile.getLocalAbsPath();
                    if (null == sourcePath) {
                        sourcePath = sourceFile.getUniquePath();
                    }
                } else {
                    sourcePath = source.getUniquePath();
                }
            }
            return new SearchHit(caseDisplayName, caseDirectoryPath, dataSourceName, sourceType, sourceName, sourcePath);
        } catch (SQLException | TskCoreException ex) {
            throw new MultiCaseSearcherException(Bundle.MultiCaseSearcher_exceptionMessage_hitProcessingError(solrObjectId, caseInfo.getCaseMetadata().getCaseName(), caseInfo.getCaseMetadata().getCaseDirectory()), ex);
        }
    }

    /**
     * Gets the Sleuthkit object id that corresponds to the Solr object id of
     * some content.
     *
     * @param solrObjectId A solr object id for some content.
     * @param caseDatabase The case database for the case that includes the
     *                     content.
     *
     * @return The Sleuthkit object id of the content.
     *
     * @throws MultiCaseSearcherException
     * @throws TskCoreException
     * @throws SQLException
     */
    private static long getObjectIdForSolrObjectId(long solrObjectId, SleuthkitCase caseDatabase) throws MultiCaseSearcherException, TskCoreException, SQLException {
        if (0 < solrObjectId) {
            return solrObjectId;
        } else {
            try (SleuthkitCase.CaseDbQuery databaseQuery = caseDatabase.executeQuery("SELECT artifact_obj_id FROM blackboard_artifacts WHERE artifact_id = " + solrObjectId)) {
                final ResultSet resultSet = databaseQuery.getResultSet();
                if (resultSet.next()) {
                    return resultSet.getLong("artifact_obj_id");
                } else {
                    throw new TskCoreException("Empty result set getting obj_id for artifact with artifact_id =" + solrObjectId);
                }
            }
        }
    }

    /**
     * Checks to see if the current thread has been interrupted (i.e, the search
     * has been cancelled) and throws an InterruptedException if it has been.
     *
     * @throws InterruptedException
     */
    private void checkForCancellation() throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || searchStopped) {
            throw new InterruptedException("Search Cancelled");
        }
    }

    /**
     * A bundle of metadata for a case.
     */
    private final static class MultiCaseMetadata {

        private final CaseMetadata caseMetadata;
        private final TextIndexMetadata textIndexMetadata;

        /**
         * Contructs a bundle of metadata for a case
         *
         * @param caseMetadata      The case metadata.
         * @param textIndexMetaData The text index metadata for the case.
         */
        private MultiCaseMetadata(CaseMetadata caseMetadata, TextIndexMetadata textIndexMetaData) {
            this.caseMetadata = caseMetadata;
            this.textIndexMetadata = textIndexMetaData;
        }

        /**
         * Gets the case metadata.
         *
         * @return The case metadata.
         */
        private CaseMetadata getCaseMetadata() {
            return this.caseMetadata;
        }

        /**
         * Gets the text index metadata for the case.
         *
         * @return The text index metadata.
         */
        private TextIndexMetadata getTextIndexMetadata() {
            return this.textIndexMetadata;
        }

    }

    /**
     * Bundles a case directory path, a Solr core fileName, and a text index UNC
     * path.
     */
    private final static class TextIndexMetadata {

        private final Path caseDirectoryPath;
        private final String solrCoreName;
        private final String textIndexUNCPath;

        /**
         * Constructs an object that bundles a Solr core fileName and a text
         * index UNC path.
         *
         * @param caseDirectoryPath The case directory path.
         * @param solrCoreName      The core fileName.
         * @param textIndexUNCPath  The text index path.
         */
        private TextIndexMetadata(Path caseDirectoryPath, String solrCoreName, String textIndexUNCPath) {
            this.caseDirectoryPath = caseDirectoryPath;
            this.solrCoreName = solrCoreName;
            this.textIndexUNCPath = textIndexUNCPath;
        }

        /**
         * Gets the case directory path.
         *
         * @return The path.
         */
        private Path getCaseDirectoryPath() {
            return this.caseDirectoryPath;
        }

        /**
         * Gets the Solr core fileName.
         *
         * @return The Solr core fileName.
         */
        private String getSolrCoreName() {
            return this.solrCoreName;
        }

        /**
         *
         * Gets the UNC path of the text index.
         *
         * @return The path.
         */
        private String getTextIndexPath() {
            return this.textIndexUNCPath;
        }

    }

    /**
     * Exception thrown if there is an error executing a search.
     */
    static final class MultiCaseSearcherException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an instance of the exception thrown if there is an error
         * executing a search.
         *
         * @param message The exception message.
         */
        private MultiCaseSearcherException(String message) {
            super(message);
        }

        /**
         * Constructs an instance of the exception thrown if there is an error
         * executing a search.
         *
         * @param message The exception message.
         * @param cause   The Throwable that caused the error.
         */
        private MultiCaseSearcherException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /**
     * Tell the MultiCaseSearcher that it's current search can be stopped the
     * next time it checks for cancellation.
     */
    void stopMultiCaseSearch() {
        //This is necessary because if the interrupt occurs during CoreAdminRequest.process, 
        //CoreAdminRequest.getStatus, or HttpSolrServer.query the interrupt gets ignored
        searchStopped = true;
    }

    /**
     * Register an object with the MultiCaseSearcher eventBus so that it's
     * subscribe methods can receive results.
     *
     * @param object the object to register with the eventBus
     */
    void registerWithEventBus(Object object) {
        eventBus.register(object);
    }

    /**
     * Unregister an object with the MultiCaseSearcher eventBus so that it's
     * subscribe methods no longer receive results.
     *
     * @param object the object to unregister with the eventBus
     */
    void unregisterWithEventBus(Object object) {
        eventBus.unregister(object);
    }

}
