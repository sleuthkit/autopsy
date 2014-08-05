/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;

/**
 *
 */
public interface Fitable {

    BooleanProperty preserveRatioProperty();

    DoubleProperty fitHeightProperty();

    DoubleProperty fitWidthProperty();

    boolean isPreserveRatio();

    double getFitHeight();

    double getFitWidth();

    void setPreserveRatio(boolean b);

    void setFitHeight(double d);

    void setFitWidth(double d);

}
