/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.Desktop;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.SwingWorker;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Action class for the Thread Dump help menu item. If there is no case open the
 * dump file will be created in PlatformUtil.getLogDirectory() otherwise the
 * file will be created in Case.getCurrentCase().getLogDirectoryPath()
 */
@ActionID(category = "Help", id = "org.sleuthkit.autopsy.actions.ThreadDumpAction")
@ActionRegistration(displayName = "#CTL_DumpThreadAction", lazy = false)
@ActionReference(path = "Menu/Help", position = 1750)
@Messages({
    "CTL_DumpThreadAction=Thread Dump"
})
public final class ThreadDumpAction extends CallableSystemAction implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ThreadDumpAction.class.getName());

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS");

    @Override
    public void performAction() {
        (new ThreadDumper()).run();
    }

    @Override
    public String getName() {
        return Bundle.CTL_DumpThreadAction();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * SwingWorker to that will create the thread dump file. Once the file is
     * created it will be opened in an external viewer.
     */
    private final class ThreadDumper extends SwingWorker<File, Void> {

        @Override
        protected File doInBackground() throws Exception {
            return createThreadDump();
        }

        @Override
        protected void done() {
            File dumpFile = null;
            try {
                dumpFile = get();
                Desktop.getDesktop().open(dumpFile);
            } catch (ExecutionException | InterruptedException ex) {
                logger.log(Level.SEVERE, "Failure occurred while creating thread dump file", ex);
            } catch (IOException ex) {
                if (dumpFile != null) {
                    logger.log(Level.WARNING, "Failed to open thread dump file in external viewer: " + dumpFile.getAbsolutePath(), ex);
                } else {
                    logger.log(Level.SEVERE, "Failed to create thread dump file.", ex);
                }
            }
        }

        /**
         * Create the thread dump file.
         *
         * @throws IOException
         */
        private File createThreadDump() throws IOException {
            File dumpFile = createFilePath().toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(dumpFile, true))) {
                ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), 100);
                for (ThreadInfo threadInfo : threadInfos) {
                    writer.write(threadInfo.toString());
                    writer.write("\n");
                }

                long[] deadlockThreadIds = threadMXBean.findDeadlockedThreads();
                if (deadlockThreadIds != null) {
                    writer.write("-------------------List of Deadlocked Thread IDs ---------------------");
                    String idsList = (Arrays
                            .stream(deadlockThreadIds)
                            .boxed()
                            .collect(Collectors.toList()))
                            .stream().map(n -> String.valueOf(n))
                            .collect(Collectors.joining("-", "{", "}"));
                    writer.write(idsList);
                }
            }

            return dumpFile;
        }

        /**
         * Create the dump file path.
         *
         * @return Path for dump file.
         */
        private Path createFilePath() {
            String fileName = "ThreadDump_" + DATE_FORMAT.format(new Date()) + ".txt";
            if (Case.isCaseOpen()) {
                return Paths.get(Case.getCurrentCase().getLogDirectoryPath(), fileName);
            }
            return Paths.get(PlatformUtil.getLogDirectory(), fileName);
        }
    }

}
