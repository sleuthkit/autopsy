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
package org.sleuthkit.autopsy.healthmonitor;

import java.util.List;
import org.sleuthkit.autopsy.healthmonitor.ServicesHealthMonitor.DatabaseTimingResult;

/**
 *
 */
class TrendLine {
    
    double slope;
    double yInt;
    
    TrendLine(List<DatabaseTimingResult> timingResults, TimingMetricGraphPanel.TimingMetricType timingMetricType) throws HealthMonitorException {
                
        if((timingResults == null) || timingResults.isEmpty()) {
            throw new HealthMonitorException("Can not generate trend line for empty/null data set");
        }
        
        // Calculate intermediate values
        int n = timingResults.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXsquared = 0;
        for(int i = 0;i < n;i++) {
            double x = timingResults.get(i).getTimestamp();
            double y;
            switch (timingMetricType) {
                case MAX:
                    y = timingResults.get(i).getMax();
                    break;
                case MIN:
                    y = timingResults.get(i).getMin();
                    break;
                case AVERAGE:
                default:
                    y = timingResults.get(i).getAverage();
                    break;
            }
            
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXsquared += x * x;
        }
        
        // Calculate slope
        slope = (n * sumXY - sumX * sumY) / (n * sumXsquared - sumX * sumX);
        
        // Calculate y intercept
        yInt = (sumY - slope * sumX) / n;
        
        System.out.println("Trend line: y = " + slope + " * x + " + yInt);
    }
    
    double getExpectedValueAt(double x) {
        return (slope * x + yInt);
    }
}
