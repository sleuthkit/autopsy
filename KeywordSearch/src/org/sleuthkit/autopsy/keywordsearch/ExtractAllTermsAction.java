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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.io.IOException;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Action for accessing the Search Other Cases dialog.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.keywordsearch.ExtractAllTermsAction")
@ActionRegistration(displayName = "#CTL_OtherCasesSearchAction=Search All Cases", lazy = false)
@ActionReference(path = "Menu/Tools", position = 202)
@NbBundle.Messages({"CTL_ExtractAllTermsAction=Extract Unique Words"})
public class ExtractAllTermsAction extends CallableSystemAction {

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        performAction();
    }

    @Override
    public void performAction() {
        // ELTODO AllCasesSearchDialog dialog = new AllCasesSearchDialog();
        // ELTODO dialog.display();
        final Server server = KeywordSearch.getServer();
        Long dsID = Long.valueOf(4);
        try {
            server.extractAllTermsForDataSource(dsID);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @NbBundle.Messages({
        "ExtractAllTermsAction.getName.text=Extract Unique Words"})
    @Override
    public String getName() {
        return Bundle.ExtractAllTermsAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
}
