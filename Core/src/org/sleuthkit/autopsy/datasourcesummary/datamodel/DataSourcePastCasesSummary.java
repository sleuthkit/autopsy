/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;

/**
 *
 * @author gregd
 */
public class DataSourcePastCasesSummary {
    private static final String CENTRAL_REPO_INGEST_NAME = CentralRepoIngestModuleFactory.getModuleName();
    
    private static final String CASE_SEPARATOR = ",";
    private static final String PREFIX_END = ":";

    private final SleuthkitCaseProvider caseProvider;
    private final java.util.logging.Logger logger;

    

            
    
    /**
     * Main constructor.
     */
    public DataSourcePastCasesSummary() {
        this(SleuthkitCaseProvider.DEFAULT,
                org.sleuthkit.autopsy.coreutils.Logger.getLogger(DataSourceUserActivitySummary.class.getName()));
        
        
    }

    private static final BlackboardAttribute.Type TYPE_COMMENT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COMMENT);

    /**
     * Gets a list of cases from the TSK_COMMENT of an artifact. The cases
     * string is expected to be of a form of "Previous Case:
     * case1,case2...caseN".
     *
     * @param artifact The artifact
     *
     * @return The list of cases if found or empty list if not.
     */
    private static List<String> getCases(BlackboardArtifact artifact) {
        String casesString = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_COMMENT);
        if (StringUtils.isBlank(casesString)) {
            return Collections.emptyList();
        }

        int prefixCharIdx = casesString.indexOf(PREFIX_END);
        if (prefixCharIdx < 0 || prefixCharIdx >= casesString.length() - 1) {
            return Collections.emptyList();
        }

        String justCasesStr = casesString.substring(prefixCharIdx + 1).trim();
        return Arrays.asList(justCasesStr.split(CASE_SEPARATOR));
    }
    
    

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider The object providing the current SleuthkitCase.
     * @param logger   The logger to use.
     */
    public DataSourcePastCasesSummary(
            SleuthkitCaseProvider provider,
            java.util.logging.Logger logger) {

        this.caseProvider = provider;
        this.logger = logger;
    }

    public List<Pair<String, Long>> getPastCasesWithNotableFile(DataSource dataSource) {

    }

    public List<Pair<String, Long>> getPastCasesWithSameId(DataSource dataSource) {

    }
}
