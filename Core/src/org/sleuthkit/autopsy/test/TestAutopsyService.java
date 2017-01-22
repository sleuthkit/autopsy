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
package org.sleuthkit.autopsy.test;

import java.util.logging.Level;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.framework.AutopsyService;
import org.sleuthkit.autopsy.framework.ProgressIndicator;

/**
 * An implementation of the Autopsy service interface used for test purposes.
 */
//@ServiceProvider(service = AutopsyService.class)
public class TestAutopsyService implements AutopsyService {

    private static final Logger LOGGER = Logger.getLogger(TestAutopsyService.class.getName());

    @Override
    public String getServiceName() {
        return "Test Autopsy Service";
    }

    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        ProgressIndicator progressIndicator = context.getProgressIndicator();
        try {
            progressIndicator.start("Doing first task...", 100);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            Thread.sleep(1000L);
            progressIndicator.progress(10);
            progressIndicator.finish("First task completed.");
            progressIndicator.start("Doing second task...");
            Thread.sleep(10000L);
            progressIndicator.finish("Second task completed.");
        } catch (InterruptedException ex) {
            LOGGER.log(Level.INFO, "Autopsy Test Service caught interrupt while working");
            if (context.cancelRequested()) {
                progressIndicator.finish("Cancelling...");
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ex1) {
                    LOGGER.log(Level.INFO, "Autopsy Test Service caught interrupt while working");
                }
            }
        }
    }

}
