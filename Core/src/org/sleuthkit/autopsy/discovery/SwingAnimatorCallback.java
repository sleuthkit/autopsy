/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.discovery;

/**
 *
 * Callback interface to be notified by a SwingAnimator of a new time frame.  *
 * @author Greg Cope
 *
 *
 *
 */
public interface SwingAnimatorCallback {

    /**
     *
     * Callback method for the SwingAnimator
     *
     * @param caller
     *
     */
    public void callback(Object caller);

    /**
     *
     * Returns true if the SwingAnimator should terminate.      *
     * @return
     *
     */
    public boolean hasTerminated();

}
