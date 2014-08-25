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
package org.sleuthkit.autopsy.imageanalyzer.filtering;

import org.sleuthkit.autopsy.imageanalyzer.EurekaController;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.FilterSet;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.AtomicFilter;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.AttributeFilter;
import org.sleuthkit.autopsy.imageanalyzer.EurekaModule;
import org.sleuthkit.autopsy.imageanalyzer.LoggedTask;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.FXMLConstructor;
import org.sleuthkit.autopsy.imageanalyzer.FileUpdateEvent;
import org.sleuthkit.autopsy.imageanalyzer.FileUpdateListener;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.AbstractFilter;
import org.sleuthkit.autopsy.imageanalyzer.filtering.filters.NameFilter;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupManager;
import org.sleuthkit.autopsy.imageanalyzer.grouping.GroupSortBy;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Callback;
import javax.swing.SortOrder;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.ThreadUtils;

/**
 * This singleton acts as the controller for the Filters. It creates filters
 * based on values in the database, and broadcasts events when the activation
 * state or other ui configuration of the filters changes.
 *
 * deprecated until we revisit filtering
 */
@Deprecated
public class FiltersPanel extends AnchorPane implements FileUpdateListener {

    public static final String FILTER_STATE_CHANGED = "FILTER_STATE_CHANGED";
    @FXML
    private ResourceBundle resources;
    @FXML
    private URL location;
    @FXML
    private ListView< AttributeFilter> filtersList;
    private static final Logger LOGGER = Logger.getLogger(FiltersPanel.class.getName());
    volatile private DrawableDB db;
    final Map<DrawableAttribute, AttributeFilter> attrFilterMap = new HashMap<>();
    private static FiltersPanel instance;

    /**
     * clear/reset state
     */
    public void clear() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                db = null;
                filterSet.clear();
                attrFilterMap.clear();
            }
        });
    }
    /**
     * listen to changes in individual filters and forward to external listeners
     * via the pcs
     */
    private final ChangeListener filterForwardingListener = new ChangeListener<Object>() {
        @Override
        public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue) {
            pcs.firePropertyChange(new PropertyChangeEvent(observable, FILTER_STATE_CHANGED, oldValue, newValue));
        }
    };

    public FilterSet getFilterSet() {
        return filterSet;
    }
    /* top level filter */
    private FilterSet filterSet = new FilterSet();
    /**
     * {@link Service} to (re)build filterset based on values in the database
     */
    private final RebuildFiltersService rebuildFiltersService = new RebuildFiltersService();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * register a {@link PropertyChangeListener}
     *
     * @param pcl
     */
    public void addListener(PropertyChangeListener pcl) {
        pcs.addPropertyChangeListener(pcl);
    }

    @FXML
    void initialize() {
        assert filtersList != null : "fx:id=\"filtersList\" was not injected: check your FXML file 'FiltersPanel.fxml'.";

        filtersList.setItems(filterSet.subFilters);

        filtersList.setCellFactory(new Callback<ListView<AttributeFilter>, ListCell<AttributeFilter>>() {
            @Override
            public ListCell<AttributeFilter> call(ListView<AttributeFilter> p) {
                return new FilterPaneListCell();
            }
        });
        for (final DrawableAttribute attr : DrawableAttribute.getValues()) {
            if (attr != DrawableAttribute.NAME && attr != DrawableAttribute.CREATED_TIME && attr != DrawableAttribute.MODIFIED_TIME) {
                final AttributeFilter attrFilter = new AttributeFilter(attr);
                filterSet.subFilters.add(attrFilter);
                attrFilterMap.put(attr, attrFilter);
            }
        }
    }

    static synchronized public FiltersPanel getDefault() {
        if (instance == null) {
            instance = new FiltersPanel();
        }
        return instance;
    }

    private FiltersPanel() {

        FXMLConstructor.construct(this, "FiltersPanel.fxml");
    }

    public void setDB(DrawableDB drawableDb) {
        db = drawableDb;
        db.addUpdatedFileListener(this);
        rebuildFilters();
    }

    @Override
    synchronized public void handleFileUpdate(FileUpdateEvent evt) {
        //updateFilters(evt.getUpdatedFiles());
    }

    synchronized public void rebuildFilters() {
        /*
         * Platform.runLater(new Runnable() {
         * @Override
         * public void run() {
         * rebuildFiltersService.restart();
         * }
         * });
         * */
    }

    synchronized public void updateFilters(final Collection< DrawableFile> files) {
        /*
         * for (DrawableFile file : files) {
         * for (final DrawableAttribute attr : DrawableAttribute.getValues()) {
         * AttributeFilter attributeFilter;
         * AbstractFilter.FilterComparison comparison = AtomicFilter.EQUALS;
         * switch (attr.attrName) {
         * case NAME:
         * case PATH:
         * case CREATED_TIME:
         * case MODIFIED_TIME:
         * case CATEGORY:
         * case OBJ_ID:
         * case ANALYZED:
         * //fall through all attributes that don't have per value filters
         * break;
         * case HASHSET:
         * comparison = AtomicFilter.CONTAINED_IN;
         * break;
         * default:
         * //default is make one == filter for each value in database
         * attributeFilter = getFilterForAttr(attr);
         *
         * ObservableList<Object> vals =
         * FXCollections.singletonObservableList(file.getValueOfAttribute(attr));
         *
         * addFilterForAttrValues(vals, attributeFilter, comparison);
         * }
         * }
         * }
         */
    }

    synchronized private AttributeFilter getFilterForAttr(final DrawableAttribute attr) {
        AttributeFilter attributeFilter = attrFilterMap.get(attr);
        if (attributeFilter == null) {
            attributeFilter = new AttributeFilter(attr);
            attributeFilter.active.addListener(filterForwardingListener);
        }

        final AttributeFilter finalFilter = attributeFilter;

//        Platform.runLater(new Runnable() {
//            @Override
//            public void run() {
        if (filterSet.subFilters.contains(finalFilter) == false) {
            filterSet.subFilters.add(finalFilter);
        }
//            }
//        });

        attrFilterMap.put(attr, attributeFilter);
        return attributeFilter;
    }

    synchronized private void addFilterForAttrValues(List<? extends Object> vals, final AttributeFilter attributeFilter, final AbstractFilter.FilterComparison filterComparison) {
        for (final Object val : vals) {
            if (attributeFilter.containsSubFilterForValue(val) == false) {
                final AtomicFilter filter = new AtomicFilter(attributeFilter.getAttribute(), filterComparison, val);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        attributeFilter.subFilters.add(filter);
                    }
                });

                filter.active.addListener(filterForwardingListener);
//                Logger.getAnonymousLogger().log(Level.INFO, "created filter " + filter);
            }

        }
    }

    /**
     *
     */
    static class FilterPaneListCell extends ListCell<AttributeFilter> {

        private final FilterPane filterPane;

        public FilterPaneListCell() {
            super();
            filterPane = new FilterPane();
        }

        @Override
        protected void updateItem(final AttributeFilter item, final boolean empty) {
            super.updateItem(item, empty);
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        setGraphic(filterPane);
                        filterPane.setFilter(item);
                    }
                }
            });


        }
    }

    private class RebuildFiltersService extends Service<Void> {

        @Override
        protected Task<Void> createTask() {
            return new RebuildFiltersTask();
        }

        private class RebuildFiltersTask extends LoggedTask<Void> {

            public RebuildFiltersTask() {
                super("rebuilding filters", true);
            }

            @Override
            protected Void call() throws Exception {
                ThreadUtils.runAndWait(new Runnable() {
                    @Override
                    public void run() {
                        filterSet.subFilters.clear();
                        attrFilterMap.clear();
                    }
                });

                LOGGER.log(Level.INFO, "rebuilding filters started");

                for (final DrawableAttribute attr : DrawableAttribute.getValues()) {
                    AbstractFilter.FilterComparison comparison = AtomicFilter.EQUALS;
                    switch (attr.attrName) {
                        case NAME:
                            final AttributeFilter nameFilter = getFilterForAttr(attr);
                            final NameFilter filter = new NameFilter();
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    nameFilter.subFilters.add(filter);
                                }
                            });

                            filter.active.addListener(filterForwardingListener);
                            filter.filterValue.addListener(filterForwardingListener);

                            LOGGER.log(Level.INFO, "createdfilter {0}", filter);
                            break;

                        case PATH:
                        case CREATED_TIME:
                        case MODIFIED_TIME:
                        case OBJ_ID:
                            break;
                        case HASHSET:
                            comparison = AtomicFilter.CONTAINED_IN;
                            break;
                        default:

                            //default is make one == filter per attribute value in db
                            final AttributeFilter attributeFilter = getFilterForAttr(attr);
                            //TODO: FILE_COUNT is arbitrarty but maybe better than NONE, we can include file counts in labels in future
                            List<? extends Object> vals = EurekaController.getDefault().getGroupManager().findValuesForAttribute(attr, GroupSortBy.FILE_COUNT, SortOrder.DESCENDING);
                            addFilterForAttrValues(vals, attributeFilter, comparison);
                    }
                }
                return null;
            }
        }
    }
}
