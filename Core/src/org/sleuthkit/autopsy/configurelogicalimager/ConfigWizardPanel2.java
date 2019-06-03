/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.configurelogicalimager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.sleuthkit.autopsy.coreutils.Logger;

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
        configFilename = (String) wiz.getProperty("configFilename");
        config = (LogicalImagerConfig) wiz.getProperty("config");
        newFile = (boolean) wiz.getProperty("newFile");
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
    
    private String chooseFile(String title) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileFilter filter = new FileNameExtensionFilter("configuration json file", new String[] {"json"});
        fileChooser.setFileFilter(filter);
        fileChooser.setMultiSelectionEnabled(false);
        if (fileChooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getPath();
            return path;
        } else {
            return null;
        }
    }
    
    public void saveConfigFile() {
        GsonBuilder gsonBuilder = new GsonBuilder().setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        String toJson = gson.toJson(config);
        System.out.println(toJson);
        try (FileWriter fileWriter = new FileWriter(configFilename)){
            gson.toJson(config, fileWriter);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(component, "Failed to save configuration file: " 
                + configFilename + "\nReason: " + ex.getMessage());
            String newFilename = chooseFile("Save to another configuration file");
            if (newFilename != null) {
                try {                
                    gson.toJson(config, new FileWriter(newFilename));
                } catch (IOException ex1) {
                    LOGGER.log(Level.SEVERE, "Failed to save configuration file: " + newFilename, ex1);
                }
            }
        } catch (JsonIOException jioe) {
            LOGGER.log(Level.SEVERE, "Failed to save configuration file: " + configFilename, jioe);
            JOptionPane.showMessageDialog(component, "Failed to save configuration file: " 
                + configFilename + "\nReason: " + jioe.getMessage());
        }
    }

}
