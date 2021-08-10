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
package org.sleuthkit.autopsy.guicomponeontutils;

import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import javax.swing.JFileChooser;
import org.openide.windows.WindowManager;

/**
 * Helper class that will initialize JFileChooser instances in background threads.
 * 
 * On windows JFileChooser can take a long time to initialize. This helper class
 * takes the work of initializing the JFileChooser off of the EDT so that in 
 * theory the when the chooser is needed initialized and ready to be displayed 
 * to the user.
 * 
 * https://stackoverflow.com/questions/49792375/jfilechooser-is-very-slow-when-using-windows-look-and-feel
 */
public final class JFileChooserHelper {
    private final FutureTask<JFileChooser> futureFileChooser = new FutureTask<>(JFileChooser::new);
    private JFileChooser chooser;
    
    /**
     * Get a helper instance.
     * 
     * @return 
     */
    public static JFileChooserHelper getHelper() {
        return new JFileChooserHelper();
    }

    /**
     * Create a new instance of the helper class. The constructor will 
     * kick of an executor to that will execute the task of initializing the 
     * JFileChooser.
     */
    private JFileChooserHelper() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(futureFileChooser);
    }

    /**
     * Return and instance of JFileChooser to the caller.
     * 
     * This call may block the EDT if the JFileChooser initialization has not
     * completed. 
     * 
     * @return 
     */
    public JFileChooser getChooser() {
        if (chooser == null) {
            // In case this takes a moment show the wait cursor.
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            try {
                // get() will only return when the initilization of the 
                // JFileChooser has completed.
                chooser = futureFileChooser.get();
            } catch (InterruptedException | ExecutionException ex) {
                // If an exception occured create a new instance of JFileChooser.
                chooser = new JFileChooser();
            }
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        return chooser;
    }
}
