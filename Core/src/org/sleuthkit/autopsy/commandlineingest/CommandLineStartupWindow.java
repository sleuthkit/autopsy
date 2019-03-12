/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.awt.Dimension;
import javax.swing.JDialog;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.StartupWindowInterface;

/**
 * Implementation of startup window for running Autopsy from command line.
 *
 * IMPORTANT: We do NOT want to register this class as ServiceProvider. We want only
 * the default Autopsy StartupWindow to be found via
 * Lookup.getDefault().lookupAll(StartupWindowInterface.class);
 */
public class CommandLineStartupWindow extends JDialog implements StartupWindowInterface {
    
    private static final String TITLE = NbBundle.getMessage(CommandLineStartupWindow.class, "CommandLineStartupWindow.title.text");
    private static final Dimension DIMENSIONS = new Dimension(300, 50);

    public CommandLineStartupWindow() {
        super(WindowManager.getDefault().getMainWindow(), TITLE, true);
        setSize(DIMENSIONS);
        add(new CommandLinePanel());
        pack();
        setResizable(false);
    }
    
    @Override
    public void open() {
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    @Override
    public void close() {
        setVisible(false);
    }    
}
