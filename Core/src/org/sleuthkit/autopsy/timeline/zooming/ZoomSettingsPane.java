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
package org.sleuthkit.autopsy.timeline.zooming;

import java.time.temporal.ChronoUnit;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.util.StringConverter;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.VisualizationMode;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.utils.IntervalUtils;
import org.sleuthkit.autopsy.timeline.utils.RangeDivisionInfo;

/**
 * A Panel that acts as a view for a given
 * TimeLineController/FilteredEventsModel. It has sliders to provide
 * context/control over three axes of zooming (timescale, event hierarchy, and
 * description detail).
 */
public class ZoomSettingsPane extends TitledPane {

    @FXML
    private Slider descrLODSlider;

    @FXML
    private Slider typeZoomSlider;

    @FXML
    private Slider timeUnitSlider;

    @FXML
    private Label descrLODLabel;

    @FXML
    private Label typeZoomLabel;

    @FXML
    private Label timeUnitLabel;

    @FXML
    private Label zoomLabel;

    private final TimeLineController controller;
    private final FilteredEventsModel filteredEvents;

    /**
     * Constructor
     *
     * @param controller TimeLineController this panel functions as a view for.
     */
    public ZoomSettingsPane(TimeLineController controller) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        FXMLConstructor.construct(this, "ZoomSettingsPane.fxml"); // NON-NLS
    }

    @NbBundle.Messages({
        "ZoomSettingsPane.descrLODLabel.text=Description Detail:",
        "ZoomSettingsPane.typeZoomLabel.text=Event Type:",
        "ZoomSettingsPane.timeUnitLabel.text=Time Units:",
        "ZoomSettingsPane.zoomLabel.text=Zoom"})
    public void initialize() {
        timeUnitSlider.setMax(TimeUnits.values().length - 2);
        timeUnitSlider.setLabelFormatter(new TimeUnitConverter());

        typeZoomSlider.setMin(1);
        typeZoomSlider.setMax(2);
        typeZoomSlider.setLabelFormatter(new TypeZoomConverter());
        descrLODSlider.setMax(DescriptionLoD.values().length - 1);
        descrLODSlider.setLabelFormatter(new DescrLODConverter());
        descrLODLabel.setText(Bundle.ZoomSettingsPane_descrLODLabel_text());
        typeZoomLabel.setText(Bundle.ZoomSettingsPane_typeZoomLabel_text());
        timeUnitLabel.setText(Bundle.ZoomSettingsPane_timeUnitLabel_text());
        zoomLabel.setText(Bundle.ZoomSettingsPane_zoomLabel_text());

        initializeSlider(timeUnitSlider,
                () -> {
            TimeUnits requestedUnit = TimeUnits.values()[new Double(timeUnitSlider.getValue()).intValue()];
            if (requestedUnit == TimeUnits.FOREVER) {
                controller.showFullRange();
            } else {
                controller.pushTimeRange(IntervalUtils.getIntervalAround(IntervalUtils.middleOf(ZoomSettingsPane.this.filteredEvents.timeRangeProperty().get()), requestedUnit.getPeriod()));
            }
        },
                this.filteredEvents.timeRangeProperty(),
                () -> {
            RangeDivisionInfo rangeInfo = RangeDivisionInfo.getRangeDivisionInfo(this.filteredEvents.timeRangeProperty().get());
            ChronoUnit chronoUnit = rangeInfo.getPeriodSize().getChronoUnit();
            timeUnitSlider.setValue(TimeUnits.fromChronoUnit(chronoUnit).ordinal() - 1);
        });

        initializeSlider(descrLODSlider,
                () -> controller.pushDescrLOD(DescriptionLoD.values()[Math.round(descrLODSlider.valueProperty().floatValue())]),
                this.filteredEvents.descriptionLODProperty(), () -> {
            descrLODSlider.setValue(this.filteredEvents.descriptionLODProperty().get().ordinal());
        });

        initializeSlider(typeZoomSlider,
                () -> controller.pushEventTypeZoom(EventTypeZoomLevel.values()[Math.round(typeZoomSlider.valueProperty().floatValue())]),
                this.filteredEvents.eventTypeZoomProperty(),
                () -> typeZoomSlider.setValue(this.filteredEvents.eventTypeZoomProperty().get().ordinal()));

        descrLODSlider.disableProperty().bind(controller.viewModeProperty().isEqualTo(VisualizationMode.COUNTS));
    }

    /**
     * setup a slider that with a listener that is added and removed to avoid
     * circular updates.
     *
     * @param <T>                 the type of the driving property
     * @param slider              the slider that will have its change handlers
     *                            setup
     * @param sliderChangeHandler the runnable that will be executed whenever
     *                            the slider value has changed and is not
     *                            currently changing
     * @param driver              the property that drives updates to this
     *                            slider
     * @param driverChangHandler  the code to update the slider bases on the
     *                            value of the driving property. This will be
     *                            wrapped in a remove/add-listener pair to
     *                            prevent circular updates.
     */
    private <T> void initializeSlider(Slider slider, Runnable sliderChangeHandler, ReadOnlyObjectProperty<T> driver, Runnable driverChangHandler) {
        final InvalidationListener sliderListener = observable -> {
            if (slider.isValueChanging() == false) {
                sliderChangeHandler.run();
            }
        };
        slider.valueProperty().addListener(sliderListener);
        slider.valueChangingProperty().addListener(sliderListener);

        Platform.runLater(driverChangHandler);

        driver.addListener(observable -> {
            slider.valueProperty().removeListener(sliderListener);
            slider.valueChangingProperty().removeListener(sliderListener);

            Platform.runLater(() -> {
                driverChangHandler.run();
                slider.valueProperty().addListener(sliderListener);
                slider.valueChangingProperty().addListener(sliderListener);
            });
        });
    }

    //Can these be abstracted to a sort of Enum converter for use in a potential enumslider
    private static class TimeUnitConverter extends StringConverter<Double> {

        @Override
        public String toString(Double object) {
            return TimeUnits.values()[Math.min(TimeUnits.values().length - 1, object.intValue() + 1)].getDisplayName();
        }

        @Override
        public Double fromString(String string) {
            return new Integer(TimeUnits.valueOf(string).ordinal()).doubleValue();
        }
    }

    private static class TypeZoomConverter extends StringConverter<Double> {

        @Override
        public String toString(Double object) {
            return EventTypeZoomLevel.values()[object.intValue()].getDisplayName();
        }

        @Override
        public Double fromString(String string) {
            return new Integer(EventTypeZoomLevel.valueOf(string).ordinal()).doubleValue();
        }
    }

    private static class DescrLODConverter extends StringConverter<Double> {

        @Override
        public String toString(Double object) {
            return DescriptionLoD.values()[object.intValue()].getDisplayName();
        }

        @Override
        public Double fromString(String string) {
            return new Integer(DescriptionLoD.valueOf(string).ordinal()).doubleValue();
        }
    }
}
