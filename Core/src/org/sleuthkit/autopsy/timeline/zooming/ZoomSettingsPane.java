/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.util.StringConverter;
import org.controlsfx.control.Notifications;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * A Panel that acts as a view for a given
 * TimeLineController/FilteredEventsModel. It has sliders to provide
 * context/control over three axes of zooming (timescale, event hierarchy level,
 * and description level of detail).
 */
public class ZoomSettingsPane extends TitledPane {

    private static final Logger logger = Logger.getLogger(ZoomSettingsPane.class.getName());

    @FXML
    private Label zoomLabel;

    @FXML
    private Label descrLODLabel;
    @FXML
    private Slider descrLODSlider;

    @FXML
    private Label typeZoomLabel;
    @FXML
    private Slider typeZoomSlider;

    @FXML
    private Label timeUnitLabel;
    @FXML
    private Slider timeUnitSlider;

    private final TimeLineController controller;
    private final EventsModel filteredEvents;

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

        typeZoomSlider.setMin(1); //don't show ROOT_TYPE
        typeZoomSlider.setMax(TimelineEventType.HierarchyLevel.values().length - 1);
        configureSliderListeners(typeZoomSlider,
                controller::pushEventTypeZoom,
                filteredEvents.eventTypesHierarchyLevelProperty(),
                TimelineEventType.HierarchyLevel.class,
                TimelineEventType.HierarchyLevel::ordinal,
                Function.identity());
        typeZoomLabel.setText(Bundle.ZoomSettingsPane_typeZoomLabel_text());

        descrLODSlider.setMax(TimelineLevelOfDetail.values().length - 1);
        configureSliderListeners(descrLODSlider,
                controller::pushDescrLOD,
                filteredEvents.descriptionLODProperty(),
                TimelineLevelOfDetail.class,
                TimelineLevelOfDetail::ordinal,
                Function.identity());
        descrLODLabel.setText(Bundle.ZoomSettingsPane_descrLODLabel_text());
        //the description slider is only usefull in the detail view
        descrLODSlider.disableProperty().bind(controller.viewModeProperty().isEqualTo(ViewMode.COUNTS));

        /**
         * In order for the selected value in the time unit slider to correspond
         * to the amount of time used as units along the x-axis of the view, and
         * since we don't want to show "forever" as a time unit, the range of
         * the slider is restricted, and there is an offset of 1 between the
         * "real" value, and what is shown in the slider labels.
         */
        timeUnitSlider.setMax(TimeUnits.values().length - 2);
        configureSliderListeners(timeUnitSlider,
                controller::pushTimeUnit,
                filteredEvents.timeRangeProperty(),
                TimeUnits.class,
                //for the purposes of this slider we want the TimeUnit one bigger than RangeDivision indicates
                modelTimeRange -> RangeDivision.getRangeDivision(modelTimeRange, TimeLineController.getJodaTimeZone()).getPeriodSize().ordinal() - 1,
                index -> index + 1);  //compensate for the -1 above when mapping to the Enum whose displayName will be shown at index
        timeUnitLabel.setText(Bundle.ZoomSettingsPane_timeUnitLabel_text());

        //hide the whole panel in list mode
        BooleanBinding notListMode = controller.viewModeProperty().isNotEqualTo(ViewMode.LIST);
        visibleProperty().bind(notListMode);
        managedProperty().bind(notListMode);

    }

    /**
     * Configure the listeners that keep the given slider in sync with model
     * property changes, and that handle user input on the slider. The listener
     * attached to the slider is added and removed to avoid circular updates.
     *
     * Because Sliders work in terms of Doubles but represent ordered Enums that
     * are indexed by Integers, and because the model properties may not be of
     * the same type as the Enum(timeUnitSlider relates to an Interval in the
     * filteredEvents model, rather than the TimeUnits shown on the Slider), a
     * mapper is needed to convert between DriverType and Integer
     * indices(driverValueMapper). Another mapper is used to modifiy the mapping
     * from Integer index to Enum value displayed as the slider tick
     * label(labelIndexMapper).
     *
     * @param slider              The slider that we are configuring.
     *
     * @param sliderValueConsumer The consumer that will get passed the newly
     *                            selected slider value (mapped to EnumType
     *                            automatically).
     *
     * @param modelProperty       The readonly model property that this slider
     *                            should be synced to.
     *
     * @param enumClass           A type token for EnumType, ie value of type
     *                            Class<EnumType>
     *
     * @param driverValueMapper   A Function that maps from driver values of
     *                            type DriverType to Integers representing the
     *                            index of the corresponding EnumType.
     *
     * @param labelIndexMapper    A Function that maps from Integer (narrowed
     *                            slider value) to Integers representing the
     *                            index of the corresponding EnumType. Used to
     *                            compensate for slider values that do not
     *                            lineup exactly with the Enum value indices to
     *                            use as tick Labels.
     */
    @NbBundle.Messages({"ZoomSettingsPane.sliderChange.errorText=Error responding to slider value change."})

    private <DriverType, EnumType extends Enum<EnumType>> void configureSliderListeners(
            Slider slider,
            CheckedConsumer<EnumType> sliderValueConsumer,
            ReadOnlyObjectProperty<DriverType> modelProperty,
            Class<EnumType> enumClass,
            Function<DriverType, Integer> driverValueMapper,
            Function<Integer, Integer> labelIndexMapper) {

        //set the tick labels to the enum displayNames
        slider.setLabelFormatter(new EnumSliderLabelFormatter<>(enumClass, labelIndexMapper));

        //make a listener to responds to slider value changes (by updating the view)
        final InvalidationListener sliderListener = observable -> {
            //only process event if the slider value is not changing (user has released slider thumb)
            if (slider.isValueChanging() == false) {
                //convert slider value to EnumType and pass to consumer
                EnumType sliderValueAsEnum = enumClass.getEnumConstants()[Math.round((float) slider.getValue())];
                try {
                    sliderValueConsumer.accept(sliderValueAsEnum);
                } catch (TskCoreException exception) {
                    logger.log(Level.SEVERE, "Error responding to slider value change.", exception);
                    Notifications.create().owner(getScene().getWindow())
                            .text(Bundle.ZoomSettingsPane_sliderChange_errorText())
                            .showError();
                }
            }
        };
        //attach listener
        slider.valueProperty().addListener(sliderListener);
        slider.valueChangingProperty().addListener(sliderListener);

        //set intial value of slider
        slider.setValue(driverValueMapper.apply(modelProperty.get()));

        //handle changes in the model property
        modelProperty.addListener(modelProp -> {
            //remove listener to avoid circular updates
            slider.valueProperty().removeListener(sliderListener);
            slider.valueChangingProperty().removeListener(sliderListener);

            Platform.runLater(() -> {
                //sync value of slider to model property value
                slider.setValue(driverValueMapper.apply(modelProperty.get()));

                //reattach listener
                slider.valueProperty().addListener(sliderListener);
                slider.valueChangingProperty().addListener(sliderListener);
            });
        });
    }

    /**
     * StringConverter for the tick Labels of a Slider that is "backed" by an
     * Enum. Narrows the Slider's Double value to an Integer and then uses that
     * as the index of the Enum value whose displayName will be shown as the
     * tick Label
     *
     * @param <EnumType> The type of Enum that this converter works with.
     */
    static private class EnumSliderLabelFormatter<EnumType extends Enum<EnumType>> extends StringConverter<Double> {

        /**
         * A Type token for the class of Enum that this converter works with.
         */
        private final Class<EnumType> clazz;
        /**
         *
         * A Function that can be used to adjust the narrowed slider value if it
         * doesn't correspond exactly to the Enum value index.
         */
        private final Function<Integer, Integer> indexAdjsuter;

        EnumSliderLabelFormatter(Class<EnumType> enumClass, Function<Integer, Integer> indexMapper) {
            this.clazz = enumClass;
            this.indexAdjsuter = indexMapper;
        }

        @Override
        public String toString(Double dbl) {
            /* Get the displayName of the EnumType whose index is the given dbl
             * after it has been narrowed and then adjusted. At one point there
             * was an interface, DisplayNameProvider, but that was inappropriate
             * to put in TSK only to support these sliders, so now we use
             * reflection instead.
             */
            EnumType enumConstant = clazz.getEnumConstants()[indexAdjsuter.apply(dbl.intValue())];
            try {
                return (String) clazz.getMethod("getDisplayName", (Class<?>[]) null).invoke(enumConstant, (Object[]) null);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                return enumConstant.toString();
            }
        }

        @Override
        public Double fromString(String string) {
            throw new UnsupportedOperationException("This method should not be used. This EnumSliderLabelFormatter is being used in an unintended way.");
        }
    }

    /**
     * Functional interface for a consumer that throws a TskCoreException.
     *
     * @param <T>
     */
    @FunctionalInterface
    private interface CheckedConsumer<T> {

        void accept(T input) throws TskCoreException;
    }
}
