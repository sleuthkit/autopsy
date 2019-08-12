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

/**
 * The org.openide.modules.OnStart annotation tells NetBeans to invoke this
 * class's run method.
 */
@org.openide.modules.OnStart
public class OnStart implements Runnable {

    /**
     * This method is invoked by virtue of the OnStart annotation on the this
     * class
     */
    @Override
    public void run() {
        TimeLineModule.onStart();
    }
}

