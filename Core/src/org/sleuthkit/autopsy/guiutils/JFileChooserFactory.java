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
package org.sleuthkit.autopsy.guiutils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 * Factory class for initializing JFileChooser instances in a background thread.
 *
 * It is a known issue that on Windows a JFileChooser can take an indeterminate
 * amount of time to initialize. Therefore when a JFileChooser is initialized on
 * the EDT there is the potential for the UI to appear hung while initialization
 * is occurring.
 *
 * Initializing a JFileChooser in a background thread should prevent the UI from
 * hanging. Using this Factory class at component construction time should allow
 * enough time for the JFileChooser to be initialized in the background before
 * the UI user causes an event which will launch the JFileChooser. If the
 * JFileChooser is not initialized prior to the event occurring, the EDT will be
 * blocked, but the wait cursor will appear.
 *
 * https://stackoverflow.com/questions/49792375/jfilechooser-is-very-slow-when-using-windows-look-and-feel
 */
public final class JFileChooserFactory {

    private static final Logger logger = Logger.getLogger(JFileChooserFactory.class.getName());

    private final FutureTask<JFileChooser> futureFileChooser;
    private JFileChooser chooser;
    private final ExecutorService executor;

    /**
     * Create a new instance of the factory. The constructor will kick off an
     * executor to execute the initializing the JFileChooser task.
     */
    public JFileChooserFactory() {
        this(null);
    }

    /**
     * Create a new instance of the factory using a class that extends
     * JFileChooser. The class default constructor will be called to initialize
     * the class.
     *
     * The passed in Class must be public and its default constructor must be
     * public.
     *
     * @param cls Class type to initialize.
     */
    public JFileChooserFactory(Class<? extends JFileChooser> cls) {
        if (cls == null) {
            futureFileChooser = new FutureTask<>(JFileChooser::new);
        } else {
            futureFileChooser = new FutureTask<>(new ChooserCallable(cls));
        }
        
        // Append the caller class name to the thread name to add information to thread dumps.
        String threadName = "JFileChooser-background-thread";
        String callerName = getCallerClassName();
        if (callerName != null) {
            threadName += "-" + callerName;
        }

        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
        executor.execute(futureFileChooser);
    }
    
    /**
     * Get the name of the class that called this one.
     * From https://stackoverflow.com/questions/11306811/how-to-get-the-caller-class-in-java
     * 
     * @return The name of the class that requested the JFileChooser or null if not found.
     */
    private static String getCallerClassName() { 
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i=1; i<stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            
            // Look for the first class that is not this one.
            if (!ste.getClassName().equals(JFileChooserFactory.class.getName())&& ste.getClassName().indexOf("java.lang.Thread")!=0) {
                String resultClassName = ste.getClassName();
                if (resultClassName.contains(".")) {
                    // For brevity, omit the package name
                    int index = resultClassName.lastIndexOf(".") + 1;
                    if (index < resultClassName.length()) {
                        resultClassName = resultClassName.substring(index);
                    }
                }
                return resultClassName;
            }
        }
        return null;
     }

    /**
     * Return and instance of JFileChooser to the caller.
     *
     * This call may block the EDT if the JFileChooser initialization has not
     * completed.
     *
     * @return
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public JFileChooser getChooser() {
        if (chooser == null) {
            // In case this takes a moment show the wait cursor.
            try {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    // get() will only return when the initilization of the 
                    // JFileChooser has completed.
                    chooser = futureFileChooser.get();
                } catch (InterruptedException | ExecutionException ex) {
                    // An exception is generally not expected. On the off chance
                    // one does occur save the situation by created a new
                    // instance in the EDT.
                    logger.log(Level.WARNING, "Failed to initialize JFileChooser in background thread.");
                    chooser = new JFileChooser();
                }
            } finally {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                executor.shutdown();
            }
        }

        return chooser;
    }

    /**
     * Simple Callable that will initialize any subclass of JFileChooser using
     * the default constructor.
     * 
     * Note that the class and default constructor must be public for this to
     * work properly.
     */
    private class ChooserCallable implements Callable<JFileChooser> {

        private final Class<? extends JFileChooser> type;

        /**
         * Construct a new instance for the given class type.
         *
         * @param type Class type to initialize.
         */
        ChooserCallable(Class<? extends JFileChooser> type) {
            this.type = type;
        }

        @Override
        public JFileChooser call() throws Exception {
            return type.newInstance();
        }
    }
}
