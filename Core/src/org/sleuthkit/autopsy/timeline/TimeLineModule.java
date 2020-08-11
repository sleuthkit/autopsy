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
package org.sleuthkit.autopsy.timeline;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javafx.application.Platform;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Manages listeners and the controller.
 *
 */
public class TimeLineModule {

    private static final Logger logger = Logger.getLogger(TimeLineModule.class.getName());

    private static final Object controllerLock = new Object();
    private static volatile TimeLineController controller;

    /**
     * provides static utilities, can not be instantiated
     */
    private TimeLineModule() {
    }

    /**
     * Get instance of the controller for the current case
     *
     * @return the controller for the current case.
     *
     * @throws NoCurrentCaseException If there is no case open.
     * @throws TskCoreException       If there was a problem accessing the case
     *                                database.
     *
     */
    public static TimeLineController getController() throws NoCurrentCaseException, TskCoreException {
        synchronized (controllerLock) {
            if (controller == null) {
                controller = new TimeLineController(Case.getCurrentCaseThrows());
            }
            return controller;
        }
    }

    /**
     * This method is invoked by virtue of the OnStart annotation on the OnStart
     * class class
     */
    static void onStart() {
        Platform.setImplicitExit(false);
        logger.info("Setting up TimeLine listeners"); //NON-NLS

        IngestManager.getInstance().addIngestModuleEventListener(new IngestModuleEventListener());
        Case.addPropertyChangeListener(new CaseEventListener());
    }

    /**
     * Listener for case events.
     */
    static private class CaseEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (Case.Events.valueOf(evt.getPropertyName()).equals(CURRENT_CASE)) {
                if (evt.getNewValue() == null) {
                    /*
                     * Current case is closing, shut down the timeline top
                     * component and set the pre case singleton controller
                     * reference to null.
                     */
                    synchronized (controllerLock) {
                        if (controller != null) {
                            controller.shutDownTimeLineListeners();
                            SwingUtilities.invokeLater(controller::shutDownTimeLineGui);
                        }
                        controller = null;
                    }
                }
            } else {
                try {
                    getController().handleCaseEvent(evt);
                } catch (NoCurrentCaseException ignored) {
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error handling application event", ex);
                }
            }
        }
    }

    /**
     * Listener for IngestModuleEvents
     */
    static private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            try {
                getController().handleIngestModuleEvent(evt);
            } catch (NoCurrentCaseException ex) {
                // ignore
                return;
            } catch (TskCoreException ex) {
                MessageNotifyUtil.Message.error("Error creating timeline controller.");
                logger.log(Level.SEVERE, "Error creating timeline controller", ex);
            }
        }
    }
}
