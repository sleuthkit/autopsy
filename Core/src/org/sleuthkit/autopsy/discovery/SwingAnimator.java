/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 *
 * Class to animate Layouts and Fades for a given component.
 *
 *
 * @author Greg Cope
 *
 *
 *
 */
public final class SwingAnimator {

    //callback object
    private final SwingAnimatorCallback callback;

    //Timer to animate on the EDT
    private Timer timer = null;

    //duration in milliseconds betweeen each firing of the Timer
    private static final int INITIAL_DURATION = 10;
    private static int duration = INITIAL_DURATION;

    /**
     *
     * Constructs a new SwingAnimator.
     *
     *
     * @param callback The object to callback to
     *
     */
    public SwingAnimator(SwingAnimatorCallback callback) {

        this(callback, false);

    }

    /**
     *
     *
     *
     * @param callback The object to callback to
     *
     * @param start    true to automatically start the animation, false
     *                 otherwise
     *
     */
    public SwingAnimator(SwingAnimatorCallback callback, boolean start) {

        this(callback, INITIAL_DURATION, start);

    }

    /**
     *
     *
     *
     * @param callback    The object to callback to
     *
     * @param frameTiming Timing between each call to callback.
     *
     * @param start       true to automatically start the animation, false
     *                    otherwise
     *
     */
    public SwingAnimator(SwingAnimatorCallback callback, int frameTiming, boolean start) {

        this.callback = callback;

        duration = frameTiming;

        if (start) {

            start();

        }

    }

    /**
     *
     *
     *
     * @param callback    The object to callback to
     *
     * @param frameTiming Timing between each call to callback.
     *
     */
    public SwingAnimator(SwingAnimatorCallback callback, int frameTiming) {

        this(callback, frameTiming, false);

    }

    /**
     *
     * Checks if this animator is running.
     *
     * @return
     *
     */
    public boolean isRunning() {

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
    public void stop() {

        if (timer != null) {

            timer.stop();

        }

    }

    /**
     *
     * Starts the timer to fire. If the current timer is non-null and running,
     * this method will first
     *
     * stop the timer before beginning a new one. *
     */
    public void start() {

        if (timer != null && timer.isRunning()) {

            stop();

        }

        timer = new Timer(duration, new CallbackListener());

        timer.start();

    }

    /**
     *
     * ActionListener implements to be passed to the internal timer instance
     *
     * @author Greg Cope
     *
     *
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
