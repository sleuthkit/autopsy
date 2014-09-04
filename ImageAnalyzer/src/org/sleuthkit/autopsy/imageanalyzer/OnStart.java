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

import javafx.application.Platform;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 *
 * The {@link OnStart} annotation tells NetBeans to invoke this class's
 * {@link OnStart#run()} method
 */
@org.openide.modules.OnStart
public class OnStart implements Runnable {

    static private final Logger LOGGER = Logger.getLogger(OnStart.class.getName());

    /**
     * make sure that the ImageAnalyzer listeners get setup as early as
     * possible, and do other setup stuff.
     *
     * This method is invoked by virtue of the {@link OnStart} annotation on the
     * {@link ImageAnalyzerModule} class
     */
    @Override
    public void run() {
        Platform.setImplicitExit(false);

        LOGGER.info("setting up ImageAnalyzer listeners");

        IngestManager.getInstance().addIngestJobEventListener(AutopsyListener.getDefault().getIngestJobEventListener());
        IngestManager.getInstance().addIngestModuleEventListener(AutopsyListener.getDefault().getIngestModuleEventListener());

        Case.addPropertyChangeListener(AutopsyListener.getDefault().getCaseListener());
    }
}
