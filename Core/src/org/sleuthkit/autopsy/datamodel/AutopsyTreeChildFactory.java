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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CasePreferences;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * Child factory to create the top level children of the autopsy tree
 *  
 */
public final class AutopsyTreeChildFactory extends ChildFactory.Detachable<Object> { 
    
    private static final Logger logger = Logger.getLogger(AutopsyTreeChildFactory.class.getName());
    
    /**
     * Listener for handling DATA_SOURCE_ADDED events.
     */
    private final PropertyChangeListener pcl = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (eventType.equals(Case.Events.DATA_SOURCE_ADDED.toString()) &&
                Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
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

    /**
     * Creates keys for the top level children.  
     * 
     * @param list list of keys created
     * @return true, indicating that the key list is complete
     */
    @Override
    protected boolean createKeys(List<Object> list) {

        try {
            SleuthkitCase tskCase = Case.getCurrentCaseThrows().getSleuthkitCase();
           
            if (Objects.equals(CasePreferences.getGroupItemsInTreeByDataSource(), true)) {
                List<DataSource> dataSources = tskCase.getDataSources();
                
                Collections.sort(dataSources, new Comparator<DataSource>() {
                    @Override
                    public int compare(DataSource dataS1, DataSource dataS2) {
                        String dataS1Name = dataS1.getName().toLowerCase();
                        String dataS2Name = dataS2.getName().toLowerCase();
                        return dataS1Name.compareTo(dataS2Name);
                    }
                });
                
                List<DataSourceGrouping> keys = new ArrayList<>();
                dataSources.forEach((datasource) -> {
                    keys.add(new DataSourceGrouping(datasource));
                });
                list.addAll(keys);
                
                list.add(new Reports());
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
            logger.log(Level.SEVERE, "Error getting datas sources list from the database.", tskCoreException);
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
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
            return ((SleuthkitVisitableItem) key).accept(new CreateSleuthkitNodeVisitor());
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