/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.healthmonitor;

/**
 *
 */
public class TimingMetric {
    
    private final String name;
    private final long startingTimestamp;
    private Long duration;
    
    TimingMetric(String name) {
        this.name = name;
        this.startingTimestamp = System.nanoTime();
        this.duration = null;
    }
    
    /**
     * Record how long the metric was running.
     */
    void stopTiming() {
        long endingTimestamp = System.nanoTime();
        this.duration = endingTimestamp - startingTimestamp;
    }

    /**
     * Get the name of metric
     * @return name
     */
    String getName() {
        return name;
    }
    
    long getDuration() throws HealthMonitorException {
        if (duration != null) {
            return duration;
        } else {
            throw new HealthMonitorException("getDuration() called before stopTiming()");
        }
    }
}
