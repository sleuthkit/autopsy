/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configurelogicalimager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeListener;
import org.apache.commons.io.FileUtils;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Configuration Wizard Panel 2
 */
public class ConfigWizardPanel2 implements WizardDescriptor.Panel<WizardDescriptor> {

    private static final Logger LOGGER = Logger.getLogger(ConfigWizardPanel2.class.getName());

    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private ConfigVisualPanel2 component;
    private String configFilename;
    private LogicalImagerConfig config;
    private boolean newFile;
    boolean isValid = false;

    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    @Override
    public ConfigVisualPanel2 getComponent() {
        if (component == null) {
            component = new ConfigVisualPanel2();
        }
        return component;
    }

    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx("help.key.here");
    }

    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...) and
        // this condition changes (last form field filled in...) then
        // use ChangeSupport to implement add/removeChangeListener below.
        // WizardDescriptor.ERROR/WARNING/INFORMATION_MESSAGE will also be useful.
    }

    @Override
    public void readSettings(WizardDescriptor wiz) {
        // use wiz.getProperty to retrieve previous panel state
        configFilename = (String) wiz.getProperty("configFilename"); // NON-NLS
        config = (LogicalImagerConfig) wiz.getProperty("config"); // NON-NLS
        newFile = (boolean) wiz.getProperty("newFile"); // NON-NLS
        component.setConfiguration(configFilename, config, newFile);
    }

    @Override
    public void storeSettings(WizardDescriptor wiz) {
        // use wiz.putProperty to remember current panel state
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
    }

    public String getConfigFilename() {
        return configFilename;
    }

    public LogicalImagerConfig getConfig() {
        return config;
    }
    
    @NbBundle.Messages({
        "# {0} - configFilename",
        "ConfigWizardPanel2.failedToSaveConfigMsg=Failed to save configuration file: {0}",
        "# {0} - reason",
        "ConfigWizardPanel2.reason=\nReason: ",       
        "ConfigWizardPanel2.failedToSaveExeMsg=Failed to save tsk_logical_imager.exe file",
    })
    public void saveConfigFile() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .setPrettyPrinting()
                .excludeFieldsWithoutExposeAnnotation()
                .disableHtmlEscaping();
        Gson gson = gsonBuilder.create();
        String toJson = gson.toJson(config);
        try {
            List<String> lines = Arrays.asList(toJson.split("\\n"));
            FileUtils.writeLines(new File(configFilename), "UTF-8", lines, System.getProperty("line.separator")); // NON-NLS
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(component, Bundle.ConfigWizardPanel2_failedToSaveConfigMsg(configFilename)
                + Bundle.ConfigWizardPanel2_reason(ex.getMessage()));
        } catch (JsonIOException jioe) {
            LOGGER.log(Level.SEVERE, "Failed to save configuration file: " + configFilename, jioe); // NON-NLS
            JOptionPane.showMessageDialog(component, Bundle.ConfigWizardPanel2_failedToSaveConfigMsg(configFilename) 
                    + Bundle.ConfigWizardPanel2_reason(jioe.getMessage()));
        }
        try {
            writeTskLogicalImagerExe(Paths.get(configFilename).getParent());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save tsk_logical_imager.exe file", ex); // NON-NLS
            JOptionPane.showMessageDialog(component, Bundle.ConfigWizardPanel2_failedToSaveExeMsg() 
                    + Bundle.ConfigWizardPanel2_reason(ex.getMessage()));
        }
    }

    private void writeTskLogicalImagerExe(Path destDir) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("tsk_logical_imager.exe")) { // NON-NLS
            File destFile = Paths.get(destDir.toString(), "tsk_logical_imager.exe").toFile(); // NON-NLS
            FileUtils.copyInputStreamToFile(in, destFile);
        }
    }

}
