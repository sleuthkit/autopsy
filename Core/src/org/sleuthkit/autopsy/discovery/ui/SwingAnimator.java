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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 *
 * Class to animate Layouts for a given component.
 *
 * @author Greg Cope
 * https://www.algosome.com/articles/java-swing-panel-animation.html
 *
 */
final class SwingAnimator {

    //callback object
    private final SwingAnimatorCallback callback;

    //Timer to animate on the EDT
    private Timer timer = null;

    //duration in milliseconds betweeen each firing of the Timer
    private static final int INITIAL_TIMING = 30;
    private int timing = INITIAL_TIMING;

    /**
     *
     * Constructs a new SwingAnimator.
     *
     * @param callback The SwingAnimatorCallback to call.
     *
     */
    SwingAnimator(SwingAnimatorCallback callback) {
        this(callback, INITIAL_TIMING);
    }

    /**
     *
     * Constructs a new SwingAnimator.
     *
     * @param callback    The SwingAnimatorCallback to call.
     * @param frameTiming Timing between each call to callback.
     *
     */
    SwingAnimator(SwingAnimatorCallback callback, int frameTiming) {
        this.callback = callback;
        timing = frameTiming;
    }

    /**
     *
     * Checks if this animator is running.
     *
     * @return True if the animator is running, false otherwise.
     *
     */
    boolean isRunning() {
        if (timer == null) {
            return false;
        }
        return timer.isRunning();
    }

    /**
     *
     * Stops the timer
     *
     */
    void stop() {
        if (timer != null) {
            timer.stop();
        }
    }

    /**
     *
     * Starts the timer to fire. If the current timer is non-null and running,
     * this method will first stop the timer before beginning a new one.
     */
    void start() {
        if (timer != null && timer.isRunning()) {
            stop();
        }
        timer = new Timer(timing, new CallbackListener());
        timer.start();
    }

    /**
     *
     * ActionListener implements to be passed to the internal timer instance.
     *
     */
    private class CallbackListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (callback.hasTerminated()) {
                if (timer == null) {
                    throw new IllegalStateException("Callback listener should not be fired outside of SwingAnimator timer control");
                }
                timer.stop();
            }
            callback.callback(SwingAnimator.this);
        }
    }

}
