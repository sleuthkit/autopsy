/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.report;

import org.openide.util.lookup.ServiceProvider;
import javax.swing.JPanel;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 */
@ServiceProvider(service = GeneralReportModule.class)
public class CreatePortableCaseModule implements GeneralReportModule {
    private static final Logger logger = Logger.getLogger(CreatePortableCaseModule.class.getName());
    private CreatePortableCasePanel configPanel;
    
    public CreatePortableCaseModule() {
    }

    @Override
    public String getName() {
        return "Create Portable Case";
    }

    @Override
    public String getDescription() {
        return "Copies selected tagged items to a new case that will work anywhere";
    }

    @Override
    public String getRelativeFilePath() {
        return "";
    }

    @NbBundle.Messages({
        "CreatePortableCaseModule.error.noHashSetsSelected=No hash set selected for export.",
        "CreatePortableCaseModule.error.noTagsSelected=No tags selected for export."
    })
    @Override
    public void generateReport(String reportPath, ReportProgressPanel progressPanel) {
        
        
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new CreatePortableCasePanel();
        return configPanel;
    }    
}
