/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleFactory;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author gregd
 */
public class DataSourcePastCasesSummary {

    private static final String CENTRAL_REPO_INGEST_NAME = CentralRepoIngestModuleFactory.getModuleName().toUpperCase().trim();

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



    private static boolean isCentralRepoGenerated(List<String> sources) {
        if (sources == null) {
            return false;
        }
        
        return sources.stream().anyMatch((str) -> (str == null) ? false : CENTRAL_REPO_INGEST_NAME.equals(str.toUpperCase().trim()));
    }

    
    
        /**
     * Gets a list of cases from the TSK_COMMENT of an artifact. The cases
     * string is expected to be of a form of "Previous Case:
     * case1,case2...caseN".
     *
     * @param artifact The artifact
     *
     * @return The list of cases if found or empty list if not.
     */
    
    
    
    
    
    private static List<String> getCasesFromArtifact(BlackboardArtifact artifact) {
        if (artifact == null) {
            return Collections.emptyList();
        }
        
        BlackboardAttribute commentAttr = null;
        try {
            commentAttr = artifact.getAttribute(TYPE_COMMENT);    
        } catch (TskCoreException ignored) {
            // ignore if no attribute can be found
        }
        
        if (commentAttr == null) {
            return Collections.emptyList();
        }
        
        if (!isCentralRepoGenerated(commentAttr.getSources())) {
            return Collections.emptyList();
        }
        
        String commentStr = commentAttr.getValueString();
        
        int prefixCharIdx = commentStr.indexOf(PREFIX_END);
        if (prefixCharIdx < 0 || prefixCharIdx >= commentStr.length() - 1) {
            return Collections.emptyList();
        }

        String justCasesStr = commentStr.substring(prefixCharIdx + 1).trim();
        return Stream.of(justCasesStr.split(CASE_SEPARATOR))
                .map(String::trim)
                .collect(Collectors.toList());
        
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

    private List<Pair<String, Long>> getPastCases(DataSource dataSource, ARTIFACT_TYPE artifactType) 
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException {
        // get a list of case names grouped by case insensitive grouping of names
        
        Collection<List<String>> cases = this.caseProvider.get().getBlackboard().getArtifacts(artifactType.getTypeID(), dataSource.getId())
                .stream()
                // convert to list of cases where there is a TSK_COMMENT from the central repo
                .flatMap((art) -> getCasesFromArtifact(art).stream())
                // group by case insensitive compare of cases
                .collect(Collectors.groupingBy((caseStr) -> caseStr.toUpperCase().trim()))
                .values();

        return cases
                .stream()
                // get any cases where an actual case is found
                .filter((lst) -> lst != null && lst.size() > 0)
                // get non-normalized (i.e. not all caps) case name and number of items found
                .map((lst) -> Pair.of(lst.get(0), (long) lst.size()))
                // sorted descending
                .sorted((a,b) -> -Long.compare(a.getValue(), b.getValue()))
                .collect(Collectors.toList());
    }

    public List<Pair<String, Long>> getPastCasesWithNotableFile(DataSource dataSource) 
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException {
        return getPastCases(dataSource, ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
    }

    public List<Pair<String, Long>> getPastCasesWithSameId(DataSource dataSource) 
            throws SleuthkitCaseProvider.SleuthkitCaseProviderException, TskCoreException {
        return getPastCases(dataSource, ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }
}
