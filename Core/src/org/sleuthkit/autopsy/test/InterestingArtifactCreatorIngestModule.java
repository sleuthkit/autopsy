/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;

import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file ingest module that creates some interestng artifacts 
 * with attributes based on files for test purposes.
 */
@NbBundle.Messages({
    "InterestingArtifactCreatorIngestModule.exceptionMessage.errorCreatingCustomType=Error creating custom artifact type."
})
final class InterestingArtifactCreatorIngestModule extends FileIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(InterestingArtifactCreatorIngestModule.class.getName());
    private static final String MODULE_NAME = InterestingArtifactCreatorIngestModuleFactory.getModuleName();
    private static final String[] ARTIFACT_TYPE_NAMES = {"TSK_WEB_BOOKMARK", "TSK_KEYWORD_HIT", "TSK_CALLLOG"};
    private static final String[] ARTIFACT_DISPLAY_NAMES = {"Web Bookmarks", "Keyword Hits", "Call Logs"};
    private static final String INT_ARTIFACT_TYPE_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getLabel();
    private static final String INT_ARTIFACT_DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName();
    private BlackboardArtifact.Type artifactType;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            Blackboard blackboard = Case.getOpenCase().getServices().getBlackboard();
            artifactType = blackboard.getOrAddArtifactType(INT_ARTIFACT_TYPE_NAME, INT_ARTIFACT_DISPLAY_NAME);
         } catch (Blackboard.BlackboardException | NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.InterestingArtifactCreatorIngestModule_exceptionMessage_errorCreatingCustomType(), ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        /*
         * Skip directories and virtual files.
         */
        if (file.isDir() || file.isVirtual()) {
            return ProcessResult.OK;
        }

        try {
            /*
             * Add a custom artifact with one custom attribute of each value
             * type.
             */
            int randomArtIndex = (int) (Math.random() * 3);
            Blackboard blackboard = Case.getOpenCase().getServices().getBlackboard();
            BlackboardArtifact.Type artifactTypeBase = blackboard.getOrAddArtifactType(ARTIFACT_TYPE_NAMES[randomArtIndex], ARTIFACT_DISPLAY_NAMES[randomArtIndex]);
            BlackboardArtifact artifactBase = file.newArtifact(artifactTypeBase.getTypeID());
            Collection<BlackboardAttribute> baseAttributes = new ArrayList<>();
            String commentTxt;
            BlackboardAttribute baseAttr;
            switch (artifactBase.getArtifactTypeID()) {
                case 2:
                    commentTxt = "www.placeholderWebsiteDOTCOM";
                    baseAttr = new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, "Fake Web BookMark", "www.thisWebsiteIsStillFake.com");
                    baseAttributes.add(baseAttr);
                    break;
                case 9:
                    commentTxt = "fakeKeyword";
                    baseAttr = new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, "Fake Keyword Search", "Fake Keyword Preview Text");
                    BlackboardAttribute set = new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, "Fake Keyword Search", "Fake");
                    BlackboardAttribute keyword = new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, "Fake Keyword Search", "FakeKeyword");
                    baseAttributes.add(baseAttr);
                    baseAttributes.add(set);
                    baseAttributes.add(keyword);
                    break;
                case 25:
                    commentTxt = "fake phone number from";
                    baseAttr = new BlackboardAttribute(
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, "Fake Call Log Whatever", "555-555-5555");
                    baseAttributes.add(baseAttr);
                    break;
                default:
                    commentTxt = "DEPENDENT ON ARTIFACT TYPE";
                    break;
            }
            artifactBase.addAttributes(baseAttributes);
            BlackboardArtifact artifact = file.newArtifact(artifactType.getTypeID());
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, "ArtifactsAndTxt");

            BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, commentTxt);
            BlackboardAttribute att3 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, MODULE_NAME, "");
            attributes.add(att);
            attributes.add(att2);
            attributes.add(att3);
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactBase.getArtifactID()));
            artifact.addAttributes(attributes);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, String.format("Failed to process file (obj_id = %d)", file.getId()), ex);
            return ProcessResult.ERROR;
        } catch (Blackboard.BlackboardException ex) {
            Exceptions.printStackTrace(ex);
        }
        return ProcessResult.OK;
    }

}
