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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * Child factory to create the top level children of the root of the directory tree 
 *  
 */
public class RootContentChildrenFactory extends ChildFactory.Detachable<Object> implements Observer { 
    
    private static final Logger logger = Logger.getLogger(RootContentChildrenFactory.class.getName());
    private final SleuthkitCase tskCase;
    
    /**
     * Constructs the child factory for root nodes
     * @param tskCase 
     */
    public RootContentChildrenFactory(SleuthkitCase tskCase) {
        this.tskCase = tskCase;

    }

     /**
     * Listener for handling DATA_SOURCE_ADDED events.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
                refreshChildren();
            }
        }
    };
    
    @Override
    protected void addNotify() {
        super.addNotify();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.DATA_SOURCE_ADDED), pcl);
    }

    @Override
    public void update(Observable o, Object arg) {
        refresh(true);
    }
    
    /**
     * Creates keys for the top level children.  
     * 
     * @param list list of keys created
     */
    @Override
    protected boolean createKeys(List<Object> list) {

        try {
            if (UserPreferences.groupItemsInTreeByDatasource()) {
                List<DataSource> dataSources = tskCase.getDataSources();
                List<DataSourceGrouping> keys = new ArrayList<>();
                dataSources.forEach((ds) -> {
                    keys.add(new DataSourceGrouping(tskCase, ds));
                });

                list.addAll(keys);
            } else {

                List<AutopsyVisitableItem> keys = new ArrayList<>(Arrays.asList(
                        new DataSources(),
                        new Views(tskCase),
                        new Results(tskCase),
                        new Tags(),
                        new Reports()));

                list.addAll(keys);
            }

        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error getting datas sources list form the database.", tskCoreException);
        }
        return true;
    }
    
    /**
     * Creates nodes for the top level Key
     * 
     * @param key
     * 
     * @return Node for the key, null if key is unknown.
     */
    @Override
    protected Node createNodeForKey(Object key) {
        
        if (key instanceof SleuthkitVisitableItem) {
            return ((SleuthkitVisitableItem) key).accept(new RootContentChildren.CreateSleuthkitNodeVisitor());
        } else if (key instanceof AutopsyVisitableItem) {
            return ((AutopsyVisitableItem) key).accept(new RootContentChildren.CreateAutopsyNodeVisitor());
        }
        else {
            logger.log(Level.SEVERE, "Unknown key type ", key.getClass().getName());
            return null;
        }
    }
    
    /**
     *  Refresh the children
     */
    public void refreshChildren() {
        refresh(true);
    }
}
