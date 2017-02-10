/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.testfixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file ingest module that associates custom artifacts and attributes with
 * files for test purposes.
 */
@NbBundle.Messages({
    "ErrorCreatingCustomBlackBoardType=Error creating custom blackboard type."
})
final class CustomArtifactsCreatorIngestModule extends FileIngestModuleAdapter {

    private static final Logger logger = Logger.getLogger(CustomArtifactsCreatorIngestModule.class.getName());
    private static final String MODULE_NAME = CustomArtifactsCreatorIngestModuleFactory.getModuleName();
    private static final String ARTIFACT_TYPE_NAME = "CUSTOM_ARTIFACT";
    private static final String ARTIFACT_DISPLAY_NAME = "Custom Artifact";
    private static final String INT_ATTR_TYPE_NAME = "CUSTOM_INT_ATTRIBUTE";
    private static final String INT_ATTR_DISPLAY_NAME = "Custom Integer";
    private static final String DOUBLE_ATTR_TYPE_NAME = "CUSTOM_DOUBLE_ATTRIBUTE";
    private static final String DOUBLE_ATTR_DISPLAY_NAME = "Custom Double";
    private static final String LONG_ATTR_TYPE_NAME = "CUSTOM_LONG_ATTRIBUTE";
    private static final String LONG_ATTR_DISPLAY_NAME = "Custom Long";
    private static final String DATETIME_ATTR_TYPE_NAME = "CUSTOM_DATETIME_ATTRIBUTE";
    private static final String DATETIME_ATTR_DISPLAY_NAME = "Custom Datetime";
    private static final String BYTES_ATTR_TYPE_NAME = "CUSTOM_BYTES_ATTRIBUTE";
    private static final String BYTES_ATTR_DISPLAY_NAME = "Custom Bytes";
    private static final String STRING_ATTR_TYPE_NAME = "CUSTOM_STRING_ATTRIBUTE";
    private static final String STRING_ATTR_DISPLAY_NAME = "Custom String";
    private BlackboardArtifact.Type artifactType;
    private BlackboardAttribute.Type intAttrType;
    private BlackboardAttribute.Type doubleAttrType;
    private BlackboardAttribute.Type longAttributeType;
    private BlackboardAttribute.Type dateTimeAttrType;
    private BlackboardAttribute.Type bytesAttrType;
    private BlackboardAttribute.Type stringAttrType;

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try {
            artifactType = blackboard.getOrAddArtifactType(ARTIFACT_TYPE_NAME, ARTIFACT_DISPLAY_NAME);
            intAttrType = blackboard.getOrAddAttributeType(INT_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER, INT_ATTR_DISPLAY_NAME);
            doubleAttrType = blackboard.getOrAddAttributeType(DOUBLE_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE, DOUBLE_ATTR_DISPLAY_NAME);
            longAttributeType = blackboard.getOrAddAttributeType(LONG_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, LONG_ATTR_DISPLAY_NAME);
            dateTimeAttrType = blackboard.getOrAddAttributeType(DATETIME_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME, DATETIME_ATTR_DISPLAY_NAME);
            bytesAttrType = blackboard.getOrAddAttributeType(BYTES_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE, BYTES_ATTR_DISPLAY_NAME);
            stringAttrType = blackboard.getOrAddAttributeType(STRING_ATTR_TYPE_NAME, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING, STRING_ATTR_DISPLAY_NAME);
        } catch (Blackboard.BlackboardException ex) {
            throw new IngestModuleException(Bundle.ErrorCreatingCustomBlackBoardType(), ex);
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
            BlackboardArtifact artifact = file.newArtifact(artifactType.getTypeID());
            List<BlackboardAttribute> attributes = new ArrayList<>();
            attributes.add(new BlackboardAttribute(intAttrType, MODULE_NAME, 0));
            attributes.add(new BlackboardAttribute(doubleAttrType, MODULE_NAME, 0.0));
            attributes.add(new BlackboardAttribute(longAttributeType, MODULE_NAME, 0L));
            attributes.add(new BlackboardAttribute(dateTimeAttrType, MODULE_NAME, 60L));
            attributes.add(new BlackboardAttribute(bytesAttrType, MODULE_NAME, DatatypeConverter.parseHexBinary("ABCD")));
            attributes.add(new BlackboardAttribute(stringAttrType, MODULE_NAME, "Zero"));
            artifact.addAttributes(attributes);

            /*
             * Add a second source module to the attributes. Try to do it twice.
             * The second attempt should have no effect on the data.
             */
            for (BlackboardAttribute attr : attributes) {
                attr.addSource("Added Module");
                attr.addSource("Added Module");
            }

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to process file (obj_id = %d)", file.getId()), ex);
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

}
