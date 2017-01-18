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
package org.sleuthkit.autopsy.casemodule;

import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.corecomponentinterfaces.ProgressIndicator;

/**
 * An implementation of the Autopsy service interface used for test purposes.
 */
@ServiceProvider(service = AutopsyService.class)
public class TestAutopsyService implements AutopsyService {

    @Override
    public String getServiceName() {
        return "TestService";
    }

    @Override
    public void openCaseResources(CaseContext context) throws AutopsyServiceException {
        ProgressIndicator progressIndicator = context.getProgressIndicator();
        try {
            progressIndicator.start("Doing first task", 100);
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
            progressIndicator.finish("First task completed");
            progressIndicator.start("Doing second task");
            Thread.sleep(10000L);
        } catch (InterruptedException ex) {
            progressIndicator.finish("Cancelling...");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ex1) {
            }
        }
    }

}
