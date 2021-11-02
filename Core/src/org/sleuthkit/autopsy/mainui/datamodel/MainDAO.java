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

/**
 * Main entry point for DAO for providing data to populate the data results
 * viewer.
 */
public class MainDAO {

    private static MainDAO instance = null;

    public synchronized static MainDAO getInstance() {
        if (instance == null) {
            instance = new MainDAO();
        }

        return instance;
    }

    private final DataArtifactDAO dataArtifactDAO = DataArtifactDAO.getInstance();
    private final AnalysisResultDAO analysisResultDAO = AnalysisResultDAO.getInstance();
    private final ViewsDAO viewsDAO = ViewsDAO.getInstance();
    private final FileSystemDAO fileSystemDAO = FileSystemDAO.getInstance();

    public DataArtifactDAO getDataArtifactsDAO() {
        return dataArtifactDAO;
    }
    
    public AnalysisResultDAO getAnalysisResultDAO() {
        return analysisResultDAO;
    }

    public ViewsDAO getViewsDAO() {
        return viewsDAO;
    }
    
    public FileSystemDAO getFileSystemDAO() {
        return fileSystemDAO;
    }
}
