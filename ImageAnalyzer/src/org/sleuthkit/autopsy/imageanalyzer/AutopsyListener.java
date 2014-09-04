/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * Singleton aggregator for listeners that hook into case and ingest modules.
 * This class depends on clients to hook up listeners to Autopsy.
 */
public class AutopsyListener {

    private static final Logger LOGGER = Logger.getLogger(AutopsyListener.class.getName());

    private final ImageAnalyzerController controller = ImageAnalyzerController.getDefault();

    private final PropertyChangeListener ingestJobEventListener = new IngestJobEventListener();

    private final PropertyChangeListener ingestModuleEventListener = new IngestModuleEventListener();

    private final PropertyChangeListener caseListener = new CaseListener();

    public PropertyChangeListener getIngestJobEventListener() {
        return ingestJobEventListener;
    }

    public PropertyChangeListener getIngestModuleEventListener() {
        return ingestModuleEventListener;
    }

    public PropertyChangeListener getCaseListener() {
        return caseListener;
    }

    private static AutopsyListener instance;

    private AutopsyListener() {
    }

    synchronized public static AutopsyListener getDefault() {
        if (instance == null) {
            instance = new AutopsyListener();
        }
        return instance;
    }

    /**
     * listener for ingest events
     */
    private class IngestJobEventListener implements PropertyChangeListener {

        @Override
        synchronized public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                //TODO can we do anything usefull here?
            }
        }
    }

    /**
     * listener for ingest events
     */
    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        synchronized public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {

                case CONTENT_CHANGED:
                    //TODO: do we need to do anything here?  -jm
                    break;
                case DATA_ADDED:
                    /* we could listen to DATA events and progressivly update
                     * files, and get data from DataSource ingest modules, but
                     * given that most modules don't post new artifacts in the
                     * events and we would have to query for them, without
                     * knowing which are the new ones, we just ignore these
                     * events for now. The relevant data should all be captured
                     * by file done event, anyways -jm */
                    break;
                case FILE_DONE:
                    /**
                     * getOldValue has fileID, getNewValue has {@link Abstractfile}
                     *
                     * {@link IngestManager#fireModuleDataEvent(org.sleuthkit.autopsy.ingest.ModuleDataEvent) fireModuleDataEvent}
                     */
                    AbstractFile file = (AbstractFile) evt.getNewValue();
                    if (controller.isListeningEnabled()) {

                        if (ImageAnalyzerModule.isSupportedAndNotKnown(file)) {
                            //this file should be included and we don't already know about it from hash sets (NSRL)
                            controller.queueTask(controller.new UpdateFileTask(file));
                        } else if (ImageAnalyzerModule.getAllSupportedExtensions().contains(file.getNameExtension())) {
                            //doing this check results in fewer tasks queued up, and faster completion of db update
                            //this file would have gotten scooped up in initial grab, but actually we don't need it
                            controller.queueTask(controller.new RemoveFile(file));
                        }
                    } else {
                        controller.setStale(true);
                        //TODO: keep track of waht we missed for later
                    }
                    break;
            }
        }
    }

    /**
     * listener for case events
     */
    private class CaseListener implements PropertyChangeListener {

        @Override
        synchronized public void propertyChange(PropertyChangeEvent evt) {

            switch (Case.Events.valueOf(evt.getPropertyName())) {
                case CURRENT_CASE:
                    Case newCase = (Case) evt.getNewValue();
                    if (newCase != null) { // case has been opened
                        //connect db, groupmanager, start worker thread
                        controller.setCase(newCase);

                    } else { // case is closing
                        //close window
                        SwingUtilities.invokeLater(ImageAnalyzerModule::closeTopComponent);
                        controller.reset();
                    }
                    break;

                case DATA_SOURCE_ADDED:
                    //copy all file data to drawable databse
                    Content newDataSource = (Content) evt.getNewValue();
                    if (controller.isListeningEnabled()) {
                        controller.queueTask(controller.new PrePopulateDataSourceFiles(newDataSource.getId()));
                    } else {
                        controller.setStale(true);
                        //TODO: keep track of what we missed for later
                    }
                    break;
            }
        }
    }

}
