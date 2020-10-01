/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

/**
 *
 * Callback interface to be notified by a SwingAnimator of a new time frame.
 *
 *
 * @author Greg Cope
 * https://www.algosome.com/articles/java-swing-panel-animation.html
 *
 */
interface SwingAnimatorCallback {

    /**
     *
     * Callback method for the SwingAnimator.
     *
     * @param caller The object which is calling the Callback.
     *
     */
    void callback(Object caller);

    /**
     *
     * Returns true if the SwingAnimator has terminated.
     *
     * @return True if the animator has terminated, false otherwise.
     *
     */
    boolean hasTerminated();

}
