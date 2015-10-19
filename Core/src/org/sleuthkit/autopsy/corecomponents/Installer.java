/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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

import java.awt.Insets;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.netbeans.swing.tabcontrol.plaf.DefaultTabbedContainerUI;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.OpenFromArguments;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Manages this module's life cycle. Opens the startup dialog during startup.
 */
public class Installer extends ModuleInstall {

    private static Installer instance;
    private static final Logger logger = Logger.getLogger(Installer.class.getName());

    public synchronized static Installer getDefault() {
        if (instance == null) {
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

        setupLAF();
        UIManager.put("ViewTabDisplayerUI", "org.sleuthkit.autopsy.corecomponents.NoTabsTabDisplayerUI");
        UIManager.put(DefaultTabbedContainerUI.KEY_VIEW_CONTENT_BORDER, BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

        /*
         * Open the passed in case, if an aut file was double clicked.
         */
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            Collection<? extends OptionProcessor> processors = Lookup.getDefault().lookupAll(OptionProcessor.class);
            for (OptionProcessor processor : processors) {
                if (processor instanceof OpenFromArguments) {
                    OpenFromArguments argsProcessor = (OpenFromArguments) processor;
                    final String caseFile = argsProcessor.getDefaultArg();
                    if (caseFile != null && !caseFile.equals("") && caseFile.endsWith(".aut") && new File(caseFile).exists()) { //NON-NLS
                        new Thread(() -> {
                            // Create case.
                            try {
                                Case.open(caseFile);
                            } catch (Exception ex) {
                                logger.log(Level.SEVERE, "Error opening case: ", ex); //NON-NLS
                            }
                        }).start();
                        return;
                    }
                }
            }
            Case.invokeStartupDialog(); // bring up the startup dialog
        });

    }

    @Override
    public void uninstalled() {
        super.uninstalled();
    }

    @Override
    public void close() {
        new Thread(() -> {
            try {
                if (Case.isCaseOpen()) {
                    Case.getCurrentCase().closeCase();
                }
            } catch (CaseActionException | IllegalStateException unused) {
                // Exception already logged. Shutting down, no need to do popup.
            }
        }).start();
    }

    private void setupLAF() {

        //TODO apply custom skinning 
        //UIManager.put("nimbusBase", new Color());
        //UIManager.put("nimbusBlueGrey", new Color());
        //UIManager.put("control", new Color());
        if (System.getProperty("os.name").toLowerCase().contains("mac")) { //NON-NLS
            setupMacOsXLAF();
        }

    }

    /**
     * Set the look and feel to be the Cross Platform 'Metal', but keep Aqua
     * dependent elements that set the Menu Bar to be in the correct place on
     * Mac OS X.
     */
    private void setupMacOsXLAF() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException ex) {
            logger.log(Level.WARNING, "Unable to set theme. ", ex); //NON-NLS
        }

        final String[] UI_MENU_ITEM_KEYS = new String[]{"MenuBarUI", //NON-NLS
    };

        Map<Object, Object> uiEntries = new TreeMap<>();

        // Store the keys that deal with menu items
        for (String key : UI_MENU_ITEM_KEYS) {
            uiEntries.put(key, UIManager.get(key));
        }

        //use Metal if available
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) { //NON-NLS
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException |
                        IllegalAccessException | UnsupportedLookAndFeelException ex) {
                    logger.log(Level.WARNING, "Unable to set theme. ", ex); //NON-NLS
                }
                break;
            }
        }

        // Overwrite the Metal menu item keys to use the Aqua versions
        uiEntries.entrySet().stream().forEach((entry) -> {
            UIManager.put(entry.getKey(), entry.getValue());
        });
    }
}
