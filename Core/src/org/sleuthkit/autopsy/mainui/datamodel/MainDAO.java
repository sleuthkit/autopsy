/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.sleuthkit.autopsy.mainui.datamodel.DataEventListener.DelegatingDataEventListener;
import org.sleuthkit.autopsy.mainui.datamodel.DataEventListener.RegisteringDataEventListener;

/**
 * Main entry point for DAO for providing data to populate the data results
 * viewer.
 */
public class MainDAO extends RegisteringDataEventListener {

    private static MainDAO instance = null;

    public synchronized static MainDAO getInstance() {
        if (instance == null) {
            instance = new MainDAO();
            instance.register();
        }

        return instance;
    }

    private final DataArtifactDAO dataArtifactDAO = DataArtifactDAO.getInstance();
    private final ViewsDAO viewsDAO = ViewsDAO.getInstance();
    private final List<DataEventListener> allDataListeners = ImmutableList.of(dataArtifactDAO, viewsDAO);

    public DataArtifactDAO getDataArtifactsDAO() {
        return dataArtifactDAO;
    }

    public ViewsDAO getViewsDAO() {
        return viewsDAO;
    }

    @Override
    public Collection<? extends DataEventListener> getDelegateListeners() {
        return allDataListeners;
    }
}
