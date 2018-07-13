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
package org.sleuthkit.autopsy.experimental.objectdetection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.highgui.Highgui;
import org.opencv.objdetect.CascadeClassifier;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.corelibs.OpenCvLoader;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Data source module to detect objects in images.
 */
public class ObjectDetectectionFileIngestModule extends FileIngestModuleAdapter {

    private final static Logger logger = Logger.getLogger(ObjectDetectectionFileIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private Map<String, CascadeClassifier> classifiers;
    private final IngestServices services = IngestServices.getInstance();
    private Blackboard blackboard;

    @Messages({"ObjectDetectionFileIngestModule.noClassifiersFound.subject=No classifiers found.",
        "# {0} - classifierDir", "ObjectDetectionFileIngestModule.noClassifiersFound.message=No classifiers were found in {0}, object detection will not be executed."})
    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        jobId = context.getJobId();
        File classifierDir = new File(PlatformUtil.getObjectDetectionClassifierPath());
        classifiers = new HashMap<>();
        //Load all classifiers found in PlatformUtil.getObjectDetectionClassifierPath()
        if (OpenCvLoader.isOpenCvLoaded() && classifierDir.exists() && classifierDir.isDirectory()) {
            for (File classifier : classifierDir.listFiles()) {
                if (classifier.isFile() && FilenameUtils.getExtension(classifier.getName()).equalsIgnoreCase("xml")) {
                    classifiers.put(classifier.getName(), new CascadeClassifier(classifier.getAbsolutePath()));
                }
            }
        } else {
            throw new IngestModule.IngestModuleException("Unable to load classifiers for object detection module.");
        }
        if (refCounter.incrementAndGet(jobId) == 1 && classifiers.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(ObjectDetectionModuleFactory.getModuleName(),
                    Bundle.ObjectDetectionFileIngestModule_noClassifiersFound_subject(),
                    Bundle.ObjectDetectionFileIngestModule_noClassifiersFound_message(PlatformUtil.getObjectDetectionClassifierPath())));
        }
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Messages({"# {0} - detectionCount", "ObjectDetectionFileIngestModule.classifierDetection.text=Classifier detected {0} object(s)"})
    @Override
    public ProcessResult process(AbstractFile file) {
        if (!classifiers.isEmpty() && ImageUtils.isImageThumbnailSupported(file)) {
            //Any image we can create a thumbnail for is one we should apply the classifiers to
            InputStream inputStream = new ReadContentInputStream(file);
            byte[] imageInMemory;
            try {
                imageInMemory = IOUtils.toByteArray(inputStream);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to read image to byte array for performing object detection on " + file.getParentPath() + file.getName() + " with object id of " + file.getId(), ex);
                return IngestModule.ProcessResult.ERROR;
            }
            Mat originalImage;
            try {
                originalImage = Highgui.imdecode(new MatOfByte(imageInMemory), Highgui.IMREAD_GRAYSCALE);
            } catch (CvException ex) {
                //The image was something which could not be decoded by OpenCv, our isImageThumbnailSupported(file) check above failed us
                logger.log(Level.WARNING, "Unable to decode image from byte array to perform object detection on " + file.getParentPath() + file.getName() + " with object id of " + file.getId(), ex); //NON-NLS
                return IngestModule.ProcessResult.ERROR;
            } catch (Exception unexpectedException) {
                //hopefully an unnecessary generic exception catch but currently present to catch any exceptions OpenCv throws which may not be documented
                logger.log(Level.SEVERE, "Unexpected Exception encountered attempting to use OpenCV to decode picture: " + file.getParentPath() + file.getName() + " with object id of " + file.getId(), unexpectedException);
                return IngestModule.ProcessResult.ERROR;
            }

            MatOfRect detectionRectangles = new MatOfRect(); //the rectangles which reprent the coordinates on the image for where objects were detected
            for (String classifierKey : classifiers.keySet()) {
                //apply each classifier to the file
                try {
                    classifiers.get(classifierKey).detectMultiScale(originalImage, detectionRectangles);
                } catch (CvException ignored) {
                    //The image was likely an image which we are unable to generate a thumbnail for, and the classifier was likely one where that is not acceptable
                    logger.log(Level.INFO, String.format("Classifier '%s' could not be applied to file '%s'.", classifierKey, file.getParentPath() + file.getName() + " with object id of " + file.getId())); //NON-NLS
                    continue;
                } catch (Exception unexpectedException) {  
                    //hopefully an unnecessary generic exception catch but currently present to catch any exceptions OpenCv throws which may not be documented
                    logger.log(Level.SEVERE, "Unexpected Exception encountered for image " + file.getParentPath() + file.getName() + " with object id of " + file.getId() +" while trying to apply classifier " + classifierKey, unexpectedException);
                    continue;
                }

                if (!detectionRectangles.empty()) {
                    //if any detections occurred create an artifact for this classifier and file combination
                    try {
                        BlackboardArtifact artifact = file.newArtifact(TSK_OBJECT_DETECTED);
                        artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION,
                                ObjectDetectionModuleFactory.getModuleName(),
                                classifierKey));
                        artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT,
                                ObjectDetectionModuleFactory.getModuleName(),
                                Bundle.ObjectDetectionFileIngestModule_classifierDetection_text((int) detectionRectangles.size().height)));

                        try {
                            /*
                             * Index the artifact for keyword search.
                             */
                            blackboard.indexArtifact(artifact);
                        } catch (Blackboard.BlackboardException ex) {
                            logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                        }

                        /*
                         * Send an event to update the view with the new result.
                         */
                        services.fireModuleDataEvent(new ModuleDataEvent(ObjectDetectionModuleFactory.getModuleName(), TSK_OBJECT_DETECTED, Collections.singletonList(artifact)));

                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, String.format("Failed to create blackboard artifact for '%s'.", file.getParentPath() + file.getName()), ex); //NON-NLS
                        return IngestModule.ProcessResult.ERROR;
                    }
                }
            }
        }

        return IngestModule.ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        refCounter.decrementAndGet(jobId);
    }
}
