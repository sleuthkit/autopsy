/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Font;
import java.awt.Insets;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import org.netbeans.swing.tabcontrol.plaf.DefaultTabbedContainerUI;
import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.StartupWindowProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Manages this module's life cycle. Opens the startup dialog during startup.
 */
public class Installer extends ModuleInstall {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static Installer instance;

    public synchronized static Installer getDefault() {
        if (null == instance) {
            instance = new Installer();
        }
        return instance;
    }

    private Installer() {
        super();
    }

    @Override
    public void restored() {
        super.restored();
        SwingUtilities.invokeLater(() -> {
            setLookAndFeel();  
        });
        UIManager.put("ViewTabDisplayerUI", "org.sleuthkit.autopsy.corecomponents.NoTabsTabDisplayerUI");
        UIManager.put(DefaultTabbedContainerUI.KEY_VIEW_CONTENT_BORDER, BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            StartupWindowProvider.getInstance().open();
        });
    }

    @Override
    public void uninstalled() {
        super.uninstalled();
    }
    
    private void setLookAndFeel() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) { //NON-NLS
            setOSXLookAndFeel();
        }else if (System.getProperty("os.name").toLowerCase().contains("nux")){
            setUnixLookAndFeel();
        }
    }

    /**
     * Set the look and feel to be the Cross Platform 'Metal', but keep Aqua
     * dependent elements that set the Menu Bar to be in the correct place on
     * Mac OS X.
     */
    private void setOSXLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            logger.log(Level.WARNING, "Error setting OS-X look-and-feel", ex); //NON-NLS
        }


        // Store the keys that deal with menu items
        final String[] UI_MENU_ITEM_KEYS = new String[]{"MenuBarUI",}; //NON-NLS    
        Map<Object, Object> uiEntries = new TreeMap<>();
        for (String key : UI_MENU_ITEM_KEYS) {
            uiEntries.put(key, UIManager.get(key));
        }

        // Use Metal if available.
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) { //NON-NLS
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    logger.log(Level.WARNING, "Error setting OS-X look-and-feel", ex); //NON-NLS
                }
                break;
            }
        }

        // Overwrite the Metal menu item keys to use the Aqua versions.
        uiEntries.entrySet().stream().forEach((entry) -> {
            UIManager.put(entry.getKey(), entry.getValue());
        });
    }
    
    public static void setUIFont (javax.swing.plaf.FontUIResource f){
    java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      Object value = UIManager.getDefaults().get(key);
      if (value instanceof javax.swing.plaf.FontUIResource)
        UIManager.put(key, f);
      }
    } 
    
    private void setModuleSettings(String value) {
        if (ModuleSettings.configExists("timeline")) {
            ModuleSettings.setConfigSetting("timeline", "enable_timeline", value);
        } else {
            ModuleSettings.makeConfigFile("timeline");
            ModuleSettings.setConfigSetting("timeline", "enable_timeline", value);
        }
    }
    
    private void setUnixLookAndFeel(){
        try {
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            setUIFont (new javax.swing.plaf.FontUIResource("DejaVu Sans Condensed", Font.PLAIN, 11));
            setModuleSettings("true");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            logger.log(Level.WARNING, "Error setting crossplatform look-and-feel, setting default look-and-feel",ex);
            setModuleSettings("false");
        }
    }
}
