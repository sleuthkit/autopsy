/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import javafx.application.Platform;

/**
 * An application start up task that sets a Platform property that makes the
 * JavaFX thread continue to run until the application calls Platform.exit.
 */
@org.openide.modules.OnStart
public class OnStart implements Runnable {

    /**
     * This task is run by NetBeans during application start up due to the
     * OnStart annotation of the class. It sets a Platform property that makes
     * the JavaFX thread continue to run until the application calls
     * Platform.exit.
     */
    @Override
    public void run() {
        Platform.setImplicitExit(false);
    }
}
