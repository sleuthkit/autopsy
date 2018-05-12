/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collections;
import java.util.List;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;

/**
 * No-Op axis that doesn't do anything usefull but is necessary to pass
 * AggregateEvent as the second member of {@link XYChart.Data} objects
 */
class EventAxis<Type extends DetailViewEvent> extends Axis<Type> {

    EventAxis(String label) {
        setLabel(label);
    }

    @Override
    public double getDisplayPosition(Type value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Type getValueForDisplay(double displayPosition) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getZeroPosition() {
        return 0;
    }

    @Override
    public boolean isValueOnAxis(Type value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double toNumericValue(Type value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Type toRealValue(double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Object autoRange(double length) {
        return null;
    }

    @Override
    protected List<Type> calculateTickValues(double length, Object range) {
        return Collections.emptyList();
    }

    @Override
    protected Object getRange() {
        return new Object();
    }

    @Override
    protected String getTickMarkLabel(Type value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void setRange(Object range, boolean animate) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
