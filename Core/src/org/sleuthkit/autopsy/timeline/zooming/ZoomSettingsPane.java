/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import java.util.function.Consumer;
import java.util.function.Function;
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
        zoomLabel.setText(Bundle.ZoomSettingsPane_zoomLabel_text());

        timeUnitSlider.setMax(TimeUnits.values().length - 1);
        timeUnitLabel.setText(Bundle.ZoomSettingsPane_timeUnitLabel_text());
        configureSliderListeners(timeUnitSlider,
                controller::pushTimeUnit,
                filteredEvents.timeRangeProperty(),
                modelTimeRange -> RangeDivisionInfo.getRangeDivisionInfo(modelTimeRange).getPeriodSize().ordinal() - 1,
                TimeUnits.class,
                dbl -> Math.min(TimeUnits.values().length - 1, dbl.intValue() + 1)
        );

        typeZoomSlider.setMin(0);
        typeZoomSlider.setMin(1);
        typeZoomSlider.setMax(EventTypeZoomLevel.values().length - 1);
        typeZoomLabel.setText(Bundle.ZoomSettingsPane_typeZoomLabel_text());
        configureSliderListeners(typeZoomSlider,
                controller::pushEventTypeZoom,
                filteredEvents.eventTypeZoomProperty(),
                EventTypeZoomLevel::ordinal,
                EventTypeZoomLevel.class,
                Double::intValue);

        descrLODSlider.setMax(DescriptionLoD.values().length - 1);
        descrLODLabel.setText(Bundle.ZoomSettingsPane_descrLODLabel_text());
        configureSliderListeners(descrLODSlider,
                controller::pushDescrLOD,
                filteredEvents.descriptionLODProperty(),
                DescriptionLoD::ordinal,
                DescriptionLoD.class,
                Double::intValue);
        descrLODSlider.disableProperty().bind(controller.viewModeProperty().isEqualTo(VisualizationMode.COUNTS));
    }

    /**
     * Configure the listeners that keep the sliders in sync with model changes,
     * and react to user input on the sliders. The listener attached to the
     * slider is added and removed to avoid circular updates.
     *
     * @param <T>                 The type of the driving model property
     * @param <EnumType>          The type of the enum that is represented along
     *                            the slider.
     * @param slider              The slider that we are configuring
     * @param sliderValueConsumer The consumer that will get passed the newly
     *                            selected slider value (mapped to EnumType
     *                            automatically)
     * @param modelProperty       The readonly model property that this slider
     *                            should be synced to.
     * @param driverValueMapper   A Function that maps from driver values of
     *                            type T to Integers representing the ordinal
     *                            index of the corresponding EnumType
     * @param enumClass           A type token for EnumType, ie Class<EnumType>
     * @param converterMapper     A Function that maps from Double (slider
     *                            value) to Integers representing the ordinal
     *                            index of the corresponding EnumType
     */
    private <T, EnumType extends Enum<EnumType> & DisplayNameProvider>
            void configureSliderListeners(Slider slider,
                    Consumer<EnumType> sliderValueConsumer,
                    ReadOnlyObjectProperty<T> modelProperty,
                    Function<T, Integer> driverValueMapper,
                    final Class<EnumType> enumClass,
                    final Function<Double, Integer> converterMapper) {

        slider.setLabelFormatter(new EnumSliderConverter<>(enumClass, converterMapper));

        final InvalidationListener sliderListener = observable -> {
            if (slider.isValueChanging() == false) {
                sliderValueConsumer.accept(enumClass.getEnumConstants()[Math.round(slider.valueProperty().floatValue())]);
            }
        };
        slider.valueProperty().addListener(sliderListener);
        slider.valueChangingProperty().addListener(sliderListener);

        Platform.runLater(() -> slider.setValue(driverValueMapper.apply(modelProperty.get())));

        modelProperty.addListener(observable -> {
            Platform.runLater(() -> {
                slider.valueProperty().removeListener(sliderListener);
                slider.valueChangingProperty().removeListener(sliderListener);

                slider.setValue(driverValueMapper.apply(modelProperty.get()));

                slider.valueProperty().addListener(sliderListener);
                slider.valueChangingProperty().addListener(sliderListener);
            });
        });
    }

    static private class EnumSliderConverter<EnumType extends Enum<EnumType> & DisplayNameProvider> extends StringConverter<Double> {

        private final Class<EnumType> clazz;
        private final Function<Double, Integer> indexAdjsuter;

        EnumSliderConverter(Class<EnumType> clazz, Function<Double, Integer> indexMapper) {
            this.clazz = clazz;
            this.indexAdjsuter = indexMapper;
        }

        @Override
        public Double fromString(String string) {
            return new Double(EnumType.valueOf(clazz, string).ordinal());
        }

        @Override
        public String toString(Double object) {
            return clazz.getEnumConstants()[indexAdjsuter.apply(object)].getDisplayName();
        }
    }
}
