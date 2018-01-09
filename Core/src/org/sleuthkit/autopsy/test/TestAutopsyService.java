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
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * An implementation of the Autopsy service interface used for test purposes.
 */
//@ServiceProvider(service = AutopsyService.class)
public class TestAutopsyService implements AutopsyService {

    private static final Logger logger = Logger.getLogger(TestAutopsyService.class.getName());

    @Override
    public String getServiceName() {
        return "Test Autopsy Service";
    }

    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        ProgressIndicator progressIndicator = context.getProgressIndicator();
        try {
            progressIndicator.start("Test Autopsy Service doing first task...");
            logger.log(Level.INFO, "Test Autopsy Service simulating work on first task");
            Thread.sleep(1000L);
            progressIndicator.progress(20);
            Thread.sleep(1000L);
            progressIndicator.progress(40);
            Thread.sleep(1000L);
            progressIndicator.progress(60);
            Thread.sleep(1000L);
            progressIndicator.progress(80);
            Thread.sleep(1000L);
            progressIndicator.progress(100);
            progressIndicator.finish();
            progressIndicator.start("Test Autopsy Service doing second task...");
            for (int i = 0; i < 10000; ++i) {
                logger.log(Level.INFO, "Test Autopsy Service simulating work on second task");
                if (context.cancelRequested()) {
                    logger.log(Level.INFO, "Test Autopsy Service cancelled while doing second task, cancel requested = {0}", context.cancelRequested());
                    break;
                }
            }
            progressIndicator.finish();
        } catch (InterruptedException ex) {
            logger.log(Level.INFO, "Test Autopsy Service interrupted (cancelled) while doing first task, cancel requested = {0}", context.cancelRequested());
        }
    }

}
