/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filesearch;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;

final public class FileSearchAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static FileSearchAction instance = null;
    private static FileSearchDialog searchDialog;
    private static Long selectedDataSourceId;

    private FileSearchAction() {
        super();
        setEnabled(Case.isCaseOpen());
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
                setEnabled(evt.getNewValue() != null);
                if (searchDialog != null && evt.getNewValue() != null) {
                    searchDialog.resetCaseDependentFilters();
                }
            }
        });
    }

    public static FileSearchAction getDefault() {
        if (instance == null) {
            instance = CallableSystemAction.get(FileSearchAction.class);
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (searchDialog == null) {
            searchDialog = new FileSearchDialog();
        }
        //Preserve whatever the previously selected data source was
        selectedDataSourceId = null;
        searchDialog.setVisible(true);
    }

    @Override
    public void performAction() {
        if (searchDialog == null) {
            searchDialog = new FileSearchDialog();
        }
        //
        searchDialog.setSelectedDataSourceFilter(selectedDataSourceId);
        searchDialog.setVisible(true);
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "FileSearchAction.getName.text");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    public void showDialog(Long dataSourceId) {
        selectedDataSourceId = dataSourceId;
        performAction();

    }

    public void showDialog() {
        showDialog(null);
    }
}
