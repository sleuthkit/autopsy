/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.awt.Color;
import java.awt.Insets;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import org.netbeans.swing.tabcontrol.plaf.DefaultTabbedContainerUI;
import org.openide.modules.ModuleInstall;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Manages this module's lifecycle. Opens the startup dialog during startup.
 */
public class Installer extends ModuleInstall {

    private static Installer instance;
    
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

        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {

            @Override
            public void run() {
                Case.invokeStartupDialog(); // bring up the startup dialog
               
            }
        });

        Logger logger = Logger.getLogger(Installer.class.getName());
        //setupLAF();
        UIManager.put("ViewTabDisplayerUI", "org.sleuthkit.autopsy.corecomponents.NoTabsTabDisplayerUI");
        UIManager.put(DefaultTabbedContainerUI.KEY_VIEW_CONTENT_BORDER, BorderFactory.createEmptyBorder());
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
    }

    private void setupLAF() {

        //TODO apply custom skinning 
        //UIManager.put("nimbusBase", new Color());
        //UIManager.put("nimbusBlueGrey", new Color());
        //UIManager.put("control", new Color());


        Logger logger = Logger.getLogger(Installer.class.getName());
        //use Nimbus if available
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels() ) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException ex) {
                    logger.log(Level.WARNING, "Unable to set theme. ", ex);
                } catch (InstantiationException ex) {
                    logger.log(Level.WARNING, "Unable to set theme. ", ex);
                } catch (IllegalAccessException ex) {
                    logger.log(Level.WARNING, "Unable to set theme. ", ex);
                } catch (UnsupportedLookAndFeelException ex) {
                    logger.log(Level.WARNING, "Unable to set theme. ", ex);
                }
                break;
            }
        }
    }
}
