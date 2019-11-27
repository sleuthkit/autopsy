/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;

/**
 * Base class for views that can be hosted in the ViewFrame
 *
 */
public abstract class AbstractTimeLineView extends BorderPane {

    private static final Logger logger = Logger.getLogger(AbstractTimeLineView.class.getName());

    /**
     * Boolean property that holds true if the view does not show any events
     * with the current zoom and filter settings.
     */
    private final ReadOnlyBooleanWrapper hasVisibleEvents = new ReadOnlyBooleanWrapper(true);

    /**
     * Boolean property that holds true if the view may not represent the
     * current state of the DB, because, for example, tags have been updated but
     * the view. was not refreshed.
     */
    private final ReadOnlyBooleanWrapper needsRefresh = new ReadOnlyBooleanWrapper(false);

    /**
     * Listener that is attached to various properties that should trigger a
     * view update when they change.
     */
    private InvalidationListener updateListener = (Observable any) -> refresh();

    /**
     * Task used to reload the content of this view
     */
    private Task<Boolean> updateTask;

    private final TimeLineController controller;
    private final EventsModel filteredEvents;

    /**
     * Constructor
     *
     * @param controller
     */
    public AbstractTimeLineView(TimeLineController controller) {
        this.controller = controller;
        this.filteredEvents = controller.getEventsModel();
        this.filteredEvents.registerForEvents(this);
        this.filteredEvents.modelParamsProperty().addListener(updateListener);
        TimeLineController.timeZoneProperty().addListener(updateListener);
    }

    /**
     * Handle a RefreshRequestedEvent from the events model by updating the
     * view.
     *
     * @param event The RefreshRequestedEvent to handle.
     */
    @Subscribe
    public void handleRefreshRequested(RefreshRequestedEvent event) {
        refresh();
    }
 

    /**
     * Does the view represent an out-of-date state of the DB. It might if, for
     * example, tags have been updated but the view was not refreshed.
     *
     * @return True if the view does not represent the current state of the DB.
     */
    public boolean needsRefresh() {
        return needsRefresh.get();
    }

    /**
     * Get a ReadOnlyBooleanProperty that holds true if this view does not
     * represent the current state of the DB>
     *
     * @return A ReadOnlyBooleanProperty that holds the out-of-date state for
     *         this view.
     */
    public ReadOnlyBooleanProperty needsRefreshProperty() {
        return needsRefresh.getReadOnlyProperty();
    }

    /**
     * Get the TimelineController for this view.
     *
     * @return The TimelineController for this view.
     */
    protected TimeLineController getController() {
        return controller;
    }

    /**
     * Refresh this view based on current state of zoom / filters. Primarily
     * this invokes the background ViewRefreshTask returned by getUpdateTask(),
     * which derived classes must implement.
     *
     * TODO: replace this logic with a javafx Service ? -jm
     */
    protected final synchronized void refresh() {
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask = null;
        }
        updateTask = getNewUpdateTask();
        updateTask.stateProperty().addListener((Observable observable) -> {
            switch (updateTask.getState()) {
                case CANCELLED:
                case FAILED:
                case READY:
                case RUNNING:
                case SCHEDULED:
                    break;
                case SUCCEEDED:
                    try {
                        this.hasVisibleEvents.set(updateTask.get());
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.log(Level.SEVERE, "Unexpected exception updating view", ex); //NON-NLS
                    }
                    break;
            }
        });
        getController().monitorTask(updateTask);
    }

    /**
     * Get the FilteredEventsModel for this view.
     *
     * @return The FilteredEventsModel for this view.
     */
    protected EventsModel getEventsModel() {
        return filteredEvents;
    }

    /**
     * Get a new background Task that fetches the appropriate data and loads it
     * into this view.
     *
     * @return A new task to execute on a background thread to reload this view
     *         with different data.
     */
    protected abstract Task<Boolean> getNewUpdateTask();

    /**
     * Get the ViewMode for this view.
     *
     * @return The ViewMode for this view.
     */
    protected abstract ViewMode getViewMode();

    /**
     * Get a List of Nodes containing settings widgets to insert into top
     * ToolBar of the ViewFrame.
     *
     * @return The List of settings Nodes.
     */
    abstract protected ImmutableList<Node> getSettingsControls();

    /**
     * Does this view have custom time navigation controls that should replace
     * the default ones from the ViewFrame?
     *
     * @return True if this view have custom time navigation controls.
     */
    abstract protected boolean hasCustomTimeNavigationControls();

    /**
     * Get a List of Nodes containing controls to insert into the lower time
     * range ToolBar of the ViewFrame.
     *
     * @return The List of Nodes.
     */
    abstract protected ImmutableList<Node> getTimeNavigationControls();

    /**
     * Dispose of this view and any resources it holds onto.
     */
    final synchronized void dispose() {
        //cancel and gc updateTask
        if (updateTask != null) {
            updateTask.cancel(true);
            updateTask = null;
        }
        //remvoe and gc updateListener
        this.filteredEvents.modelParamsProperty().removeListener(updateListener);
        TimeLineController.timeZoneProperty().removeListener(updateListener);
        updateListener = null;
        filteredEvents.unRegisterForEvents(this);
        controller.unRegisterForEvents(this);
    }

    /**
     * Are there are any events visible in this view with the current view
     * parameters?
     *
     * @return True if there are events visible in this view with the current
     *         view parameters.
     */
    boolean hasVisibleEvents() {
        return hasVisibleEventsProperty().get();
    }

    /**
     * A property that indicates whether there are any events visible in this
     * view with the current view parameters.
     *
     * @return A property that indicates whether there are any events visible in
     *         this view with the current view parameters.
     */
    ReadOnlyBooleanProperty hasVisibleEventsProperty() {
        return hasVisibleEvents.getReadOnlyProperty();
    }

    /**
     * Set this view out of date because, for example, tags have been updated
     * but the view was not refreshed.
     */
    void setNeedsRefresh() {
        needsRefresh.set(true);
    }

    /**
     * Clear all data items from this chart.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    abstract protected void clearData();

    /**
     * Base class for Tasks that refreshes a view when the view settings change.
     *
     * @param <AxisValuesType> The type of a single object that can represent
     *                         the range of data displayed along the X-Axis.
     */
    protected abstract class ViewRefreshTask<AxisValuesType> extends LoggedTask<Boolean> {

        private final Node center;

        /**
         * Constructor
         *
         * @param taskName        The name of this task.
         * @param logStateChanges Whether or not task state changes should be
         *                        logged.
         */
        protected ViewRefreshTask(String taskName, boolean logStateChanges) {
            super(taskName, logStateChanges);
            this.center = getCenter();
        }

        /**
         * Sets initial progress value and message and shows blocking progress
         * indicator over the view. Derived Tasks should be sure to call this as
         * part of their call() implementation.
         *
         * @return True
         *
         * @throws Exception If there is an unhandled exception during the
         *                   background operation
         */
        @NbBundle.Messages(value = {"ViewRefreshTask.preparing=Analyzing zoom and filter settings"})
        @Override
        protected Boolean call() throws Exception {
            updateProgress(-1, 1);
            updateMessage(Bundle.ViewRefreshTask_preparing());
            Platform.runLater(() -> {
                MaskerPane maskerPane = new MaskerPane();
                maskerPane.textProperty().bind(messageProperty());
                maskerPane.progressProperty().bind(progressProperty());
                setCenter(new StackPane(center, maskerPane));
                setCursor(Cursor.WAIT);
            });
            return true;
        }

        /**
         * Updates the horizontal axis and removes the blocking progress
         * indicator. Derived Tasks should be sure to call this as part of their
         * succeeded() implementation.
         */
        @Override
        protected void succeeded() {
            super.succeeded();
            needsRefresh.set(false);
            cleanup();
        }

        /**
         * Removes the blocking progress indicator. Derived Tasks should be sure
         * to call this as part of their cancelled() implementation.
         */
        @Override
        protected void cancelled() {
            super.cancelled();
            cleanup();
        }

        /**
         * Removes the blocking progress indicator. Derived Tasks should be sure
         * to call this as part of their failed() implementation.
         */
        @Override
        protected void failed() {
            super.failed();
            cleanup();
        }

        /**
         * Removes the blocking progress indicator and reset the cursor to the
         * default.
         */
        private void cleanup() {
            setCenter(center); //clear masker pane installed in call()
            setCursor(Cursor.DEFAULT);
        }

        /**
         * Set the horizontal range that this chart will show.
         *
         * @param values A single object representing the range that this chart
         *               will show.
         */
        protected abstract void setDateValues(AxisValuesType values);

        /**
         * Clears the chart data and sets the horizontal axis range. For use
         * within the derived implementation of the call() method.
         *
         * @param axisValues
         */
        @ThreadConfined(type = ThreadConfined.ThreadType.NOT_UI)
        protected void resetView(AxisValuesType axisValues) {
            Platform.runLater(() -> {
                clearData();
                setDateValues(axisValues);
            });
        }
    }
}
