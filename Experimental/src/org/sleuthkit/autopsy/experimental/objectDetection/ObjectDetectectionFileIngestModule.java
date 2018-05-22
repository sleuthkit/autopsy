/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.objectDetection;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

public class ObjectDetectectionFileIngestModule extends FileIngestModuleAdapter {

    private Map<String, CascadeClassifier> cascades;
    private final IngestServices services = IngestServices.getInstance();
    private final static Logger logger = Logger.getLogger(ObjectDetectectionFileIngestModule.class.getName());
    private Blackboard blackboard;

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.loadLibrary("opencv_java2413");
        File classifierDir = new File(PlatformUtil.getObjectDetectionClassifierPath());
        cascades = new HashMap<>();
        if (classifierDir.exists() && classifierDir.isDirectory()) {
            for (File classifier : classifierDir.listFiles()) {
                if (classifier.isFile() && FilenameUtils.getExtension(classifier.getName()).equalsIgnoreCase("xml")) {
                    cascades.put(classifier.getName(), new CascadeClassifier(classifier.getAbsolutePath()));
                }
            }
        }

        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        try {
            if (ImageUtils.isImageThumbnailSupported(file)) {
                Mat originalImage = Highgui.imread(file.getLocalPath());
                MatOfRect detectionRectangles = new MatOfRect();
//                Mat grayImage = new Mat();
                // convert the frame in gray scale
//                Imgproc.cvtColor(originalImage, grayImage, Imgproc.COLOR_BGR2GRAY);
                // equalize the frame histogram to improve the result -doesn't always
//                Imgproc.equalizeHist(grayImage, grayImage);
                for (String cascadeKey : cascades.keySet()) {
                    cascades.get(cascadeKey).detectMultiScale(originalImage, detectionRectangles);

                    String comment = "Classifier detected " + (int) detectionRectangles.size().height + " object(s)";
                    System.out.println(comment);
                    if (!detectionRectangles.empty()) {
                        try {
                            BlackboardArtifact artifact = file.newArtifact(TSK_OBJECT_DETECTED);
                            artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION, ObjectDetectionModuleFactory.getModuleName(), cascadeKey));
                            artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, ObjectDetectionModuleFactory.getModuleName(), comment));

                            try {
                                /*
                                 * Index the artifact for keyword search.
                                 */
                                blackboard.indexArtifact(artifact);
                            } catch (Blackboard.BlackboardException ex) {
                                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                            }

                            /*
                             * Send an event to update the view with the new
                             * result.
                             */
                            services.fireModuleDataEvent(new ModuleDataEvent(ObjectDetectionModuleFactory.getModuleName(), TSK_OBJECT_DETECTED, Collections.singletonList(artifact)));
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, String.format("Failed to create blackboard artifact for '%s'.", file.getParentPath() + file.getName()), ex); //NON-NLS
                            return IngestModule.ProcessResult.ERROR;
                        }
                    }
                }
            } else {
                System.out.println(file.getName() + " IS NOT A PICTURE");
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "EXCEPTION processing file: " + file.getName(), ex);
        }
        return IngestModule.ProcessResult.OK;
    }

}
