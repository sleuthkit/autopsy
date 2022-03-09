/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.ingestmodule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_VALUE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OTHER_CASES;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility methods shared by the central repository ingest modules.
 */
class CentralRepoIngestModuleUtils {

    private static final Logger LOGGER = Logger.getLogger(CentralRepoDataArtifactIngestModule.class.getName());
    private static final int MAX_PREV_CASES_FOR_NOTABLE_SCORE = 10;
    private static final int MAX_PREV_CASES_FOR_PREV_SEEN = 20;
    private final static String MODULE_NAME = CentralRepoIngestModuleFactory.getModuleName();

    /**
     * Gets any previous occurrences of a given correlation attribute in cases
     * other than the current case.
     *
     * @param corrAttr The correlation attribute.
     *
     * @return The other occurrences of the correlation attribute.
     */
    static List<CorrelationAttributeInstance> getOccurrencesInOtherCases(CorrelationAttributeInstance corrAttr, long ingestJobId) {
        List<CorrelationAttributeInstance> previousOccurrences = new ArrayList<>();
        try {
            CentralRepository centralRepo = CentralRepository.getInstance();
            previousOccurrences = centralRepo.getArtifactInstancesByTypeValue(corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
            for (Iterator<CorrelationAttributeInstance> iterator = previousOccurrences.iterator(); iterator.hasNext();) {
                CorrelationAttributeInstance prevOccurrence = iterator.next();
                if (prevOccurrence.getCorrelationCase().getCaseUUID().equals(corrAttr.getCorrelationCase().getCaseUUID())) {
                    iterator.remove();
                }
            }
        } catch (CorrelationAttributeNormalizationException ex) {
            LOGGER.log(Level.WARNING, String.format("Error normalizing correlation attribute value for 's' (job ID=%d)", corrAttr, ingestJobId), ex); // NON-NLS
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error getting previous occurences of correlation attribute 's' (job ID=%d)", corrAttr, ingestJobId), ex); // NON-NLS
        }
        return previousOccurrences;
    }

    /**
     * Makes a previously notable analysis result for a content.
     *
     * @param content         The content.
     * @param previousCases   The names of the cases in which the artifact was
     *                        deemed notable.
     * @param corrAttrType    The type of the matched correlation attribute.
     * @param corrAttrValue   The value of the matched correlation attribute.
     * @param dataSourceObjId The data source object ID.
     * @param ingestJobId     The ingest job ID.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_notableSetName=Previously Tagged As Notable (Central Repository)",
        "# {0} - list of cases",
        "CentralRepoIngestModule_notableJustification=Previously marked as notable in cases {0}"
    })
    static void makePrevNotableAnalysisResult(Content content, Set<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue, long dataSourceObjId, long ingestJobId) {
        String prevCases = previousCases.stream().collect(Collectors.joining(","));
        String justification = Bundle.CentralRepoIngestModule_notableJustification(prevCases);
        Collection<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(TSK_SET_NAME, MODULE_NAME, Bundle.CentralRepoIngestModule_notableSetName()),
                new BlackboardAttribute(TSK_CORRELATION_TYPE, MODULE_NAME, corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, MODULE_NAME, corrAttrValue),
                new BlackboardAttribute(TSK_OTHER_CASES, MODULE_NAME, prevCases));
        Optional<AnalysisResult> result = makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, attributes, "", Score.SCORE_NOTABLE, justification, dataSourceObjId, ingestJobId);
        if (result.isPresent()) {
            postNotableMessage(content, previousCases, corrAttrValue, result.get());
        }
    }

    /**
     * Makes a previously seen analysis result for a content, unless the content
     * is too common.
     *
     * @param content         The content.
     * @param previousCases   The names of the cases in which the artifact was
     *                        previously seen.
     * @param corrAttrType    The type of the matched correlation attribute.
     * @param corrAttrValue   The value of the matched correlation attribute.
     * @param dataSourceObjId The data source object ID.
     * @param ingestJobId     The ingest job ID.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevSeenSetName=Previously Seen (Central Repository)",
        "# {0} - list of cases",
        "CentralRepoIngestModule_prevSeenJustification=Previously seen in cases {0}"
    })
    static void makePrevSeenAnalysisResult(Content content, Set<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue, long dataSourceObjId, long ingestJobId) {
        Optional<Score> score = calculateScore(previousCases.size());
        if (score.isPresent()) {
            String prevCases = previousCases.stream().collect(Collectors.joining(","));
            String justification = Bundle.CentralRepoIngestModule_prevSeenJustification(prevCases);
            Collection<BlackboardAttribute> analysisResultAttributes = Arrays.asList(
                    new BlackboardAttribute(TSK_SET_NAME, MODULE_NAME, Bundle.CentralRepoIngestModule_prevSeenSetName()),
                    new BlackboardAttribute(TSK_CORRELATION_TYPE, MODULE_NAME, corrAttrType.getDisplayName()),
                    new BlackboardAttribute(TSK_CORRELATION_VALUE, MODULE_NAME, corrAttrValue),
                    new BlackboardAttribute(TSK_OTHER_CASES, MODULE_NAME, prevCases));
            makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_SEEN, analysisResultAttributes, "", score.get(), justification, dataSourceObjId, ingestJobId);
        }
    }

    /**
     * Makes a previously unseen analysis result for a content.
     *
     * @param content         The content.
     * @param corrAttrType    The type of the new correlation attribute.
     * @param corrAttrValue   The value of the new correlation attribute.
     * @param dataSourceObjId The data source object ID.
     * @param ingestJobId     The ingest job ID.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevUnseenJustification=Previously seen in zero cases"
    })
    static void makePrevUnseenAnalysisResult(Content content, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue, long dataSourceObjId, long ingestJobId) {
        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(
                new BlackboardAttribute(TSK_CORRELATION_TYPE, MODULE_NAME, corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, MODULE_NAME, corrAttrValue));
        makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_UNSEEN, attributesForNewArtifact, "", Score.SCORE_LIKELY_NOTABLE, Bundle.CentralRepoIngestModule_prevUnseenJustification(), dataSourceObjId, ingestJobId);
    }

    /**
     * Calculates a score based in a number of previous cases.
     *
     * @param numPreviousCases The number of previous cases.
     *
     * @return An Optional of a score, will be empty if there is no score
     *         because the number of previous cases is too high, indicating a
     *         common and therefore uninteresting item.
     */
    static Optional<Score> calculateScore(int numPreviousCases) {
        Score score = null;
        if (numPreviousCases <= MAX_PREV_CASES_FOR_NOTABLE_SCORE) {
            score = Score.SCORE_LIKELY_NOTABLE;
        } else if (numPreviousCases > MAX_PREV_CASES_FOR_NOTABLE_SCORE && numPreviousCases <= MAX_PREV_CASES_FOR_PREV_SEEN) {
            score = Score.SCORE_NONE;
        }
        return Optional.ofNullable(score);
    }

    /**
     * Makes a new analysis result of a given type for a content and posts it to
     * the blackboard.
     *
     * @param content             The content.
     * @param analysisResultType  The type of analysis result to make.
     * @param analysisResultAttrs The attributes of the new analysis result.
     * @param configuration       The configuration for the new analysis result.
     * @param score               The score for the new analysis result.
     * @param justification       The justification for the new analysis result.
     * @param dataSourceObjId     The data source object ID.
     * @param ingestJobId         The ingest job ID.
     *
     * @return The analysis result or null if the result already existed or an
     *         error that prevented creation of the analysis result occurred.
     */
    private static Optional<AnalysisResult> makeAndPostAnalysisResult(Content content, BlackboardArtifact.Type analysisResultType, Collection<BlackboardAttribute> analysisResultAttrs, String configuration, Score score, String justification, long dataSourceObjId, long ingestJobId) {
        AnalysisResult analysisResult = null;
        try {
            Blackboard blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
            if (!blackboard.artifactExists(content, analysisResultType, analysisResultAttrs)) {
                analysisResult = content.newAnalysisResult(analysisResultType, score, null, configuration, justification, analysisResultAttrs, dataSourceObjId).getAnalysisResult();
                try {
                    blackboard.postArtifact(analysisResult, MODULE_NAME, ingestJobId);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error posting analysis result '%s' to blackboard for content 's' (job ID=%d)", analysisResult, content, ingestJobId), ex); //NON-NLS
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error creating %s analysis result for content '%s' (job ID=%d)", analysisResultType, content, ingestJobId), ex); // NON-NLS            
        }
        return Optional.ofNullable(analysisResult);
    }

    /**
     * Posts a message to the ingest messages inbox to notify the user that a
     * notable content has been found, i.e., a previously notable analysis
     * result has been created.
     *
     * @param content        The notable content.
     * @param otherCases     The other cases in which the content was marked as
     *                       notable.
     * @param corrAttrValue  The correlation attribute value used to identify
     *                       the content, used by the ingest inbox as a unique
     *                       key for message grouping.
     * @param analysisResult The previously notable analysis result.
     */
    @NbBundle.Messages({
        "# {0} - Name of item that is Notable",
        "CentralRepoIngestModule_notable_inbox_msg_subject=Notable: {0}"
    })
    private static void postNotableMessage(Content content, Set<String> otherCases, String corrAttrValue, AnalysisResult analysisResult) {
        String msgSubject = null;
        String msgDetails = null;
        String msgKey = corrAttrValue;
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            msgSubject = Bundle.CentralRepoIngestModule_notable_inbox_msg_subject(file.getName());
            msgDetails = makeNotableFileMessage(file, otherCases);
        } else if (content instanceof DataArtifact) {
            DataArtifact artifact = (DataArtifact) content;
            msgSubject = Bundle.CentralRepoIngestModule_notable_inbox_msg_subject(artifact.getDisplayName());
            msgDetails = makeNotableDataArtifactMessage(artifact, corrAttrValue, otherCases);
        } else {
            LOGGER.log(Level.SEVERE, "Unsupported Content, cannot post ingest inbox message");
        }
        if (msgSubject != null && msgDetails != null) {
            IngestServices.getInstance().postMessage(
                    IngestMessage.createDataMessage(
                            MODULE_NAME,
                            msgSubject,
                            msgDetails,
                            msgKey,
                            analysisResult));
        }
    }

    /**
     * Makes an ingest inbox message for a notable file. Uses similar HTML
     * markup as is used for this purpose by the hash lookup ingest module.
     *
     * @param file       The notable file.
     * @param otherCases The cases other than the current case in which the file
     *                   was marked as nmotable.
     *
     * @return The message.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_filename_inbox_msg_header=File Name",
        "CentralRepoIngestModule_md5Hash_inbox_msg_header=MD5 Hash",
        "CentralRepoIngestModule_prev_cases_inbox_msg_header=Previous Cases"
    })
    private static String makeNotableFileMessage(AbstractFile file, Set<String> otherCases) {
        StringBuilder message = new StringBuilder(1024);
        message.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_filename_inbox_msg_header(), file.getName());
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_md5Hash_inbox_msg_header(), file.getMd5Hash());
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_prev_cases_inbox_msg_header(), otherCases.stream().collect(Collectors.joining(",")));
        return message.toString();
    }

    /**
     * Makes an ingest inbox message for a notable data artifact. Uses similar
     * HTML markup as is used for this purpose by the hash lookup ingest module.
     *
     * @param artifact      The data artifact
     * @param corrAttrValue The notable attribute (correlation attribute value).
     * @param otherCases    The cases other than the current case in which the
     *                      artifact was marked as nmotable.
     *
     * @return The message.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_artifact_type_inbox_msg_header=Artifact Type",
        "CentralRepoIngestModule_notable_attr_inbox_msg_header=Notable Attribute"
    })
    private static String makeNotableDataArtifactMessage(DataArtifact artifact, String corrAttrValue, Set<String> otherCases) {
        StringBuilder message = new StringBuilder(1024);
        message.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_artifact_type_inbox_msg_header(), artifact.getDisplayName());
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_notable_attr_inbox_msg_header(), corrAttrValue);
        addTableRowMarkup(message, Bundle.CentralRepoIngestModule_prev_cases_inbox_msg_header(), otherCases.stream().collect(Collectors.joining(",")));
        message.append("</table>"); //NON-NLS
        return message.toString();
    }

    /**
     * Adds a table row to a notable item message (HTML).
     *
     * @param message    The string builder for the message.
     * @param headerText The table row header text.
     * @param cellText   The table row cell text.
     */
    private static void addTableRowMarkup(StringBuilder message, String headerText, String cellText) {
        message.append("<tr>"); //NON-NLS
        message.append("<th>").append(headerText).append("</th>"); //NON-NLS
        message.append("<td>").append(cellText).append("</td>"); //NON-NLS
        message.append("</tr>"); //NON-NLS
    }

    /*
     * Prevents instatiation of this utility class.
     */
    private CentralRepoIngestModuleUtils() {
    }

}
