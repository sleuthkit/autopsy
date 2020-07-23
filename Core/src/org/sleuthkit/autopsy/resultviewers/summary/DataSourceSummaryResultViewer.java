/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.resultviewers.summary;

import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.AbstractDataResultViewer;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;



/**
 *
 * @author gregd
 */
public class DataSourceSummaryResultViewer extends AbstractDataResultViewer {
    private final String title;
    
    public DataSourceSummaryResultViewer() {
        this(null);
    }
    
    @Messages({
        "DataSourceSummaryResultViewer_title=Summary"
    })
    public DataSourceSummaryResultViewer(ExplorerManager explorerManager) {
        this(explorerManager, Bundle.DataSourceSummaryResultViewer_title());
    }
    
    public DataSourceSummaryResultViewer(ExplorerManager explorerManager, String title) {
        super(explorerManager);
        this.title = title;
    }
    
    @Override
    public DataResultViewer createInstance() {
        return new DataSourceSummaryResultViewer();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        DataSource contentItem = node.getLookup().lookup(DataSource.class);
        if (contentItem == null) {
            return false;
        }
        
        Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(datasourceObjId);
    }

    @Override
    public void setNode(Node node) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getTitle() {
        return title;
    }
}
