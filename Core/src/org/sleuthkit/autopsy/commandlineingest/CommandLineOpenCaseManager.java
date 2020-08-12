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
package org.sleuthkit.autopsy.commandlineingest;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.OpenFromArguments;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Handles the opening of a case from the command line.
 *
 */
public class CommandLineOpenCaseManager extends CommandLineManager {

    private static final Logger LOGGER = Logger.getLogger(CommandLineOpenCaseManager.class.getName());

    /**
     * Starts the thread to open the case.
     */
    public void start() {
        new Thread(new CommandLineOpenCaseManager.JobProcessingTask()).start();
    }

    /**
     * A runnable class that open the given class in the list of command line
     * options.
     */
    private final class JobProcessingTask implements Runnable {

        @Override
        public void run() {
            String casePath = "";

            // first look up all OptionProcessors and get input data from CommandLineOptionProcessor
            Collection<? extends OptionProcessor> optionProcessors = Lookup.getDefault().lookupAll(OptionProcessor.class);
            Iterator<? extends OptionProcessor> optionsIterator = optionProcessors.iterator();
            while (optionsIterator.hasNext()) {
                // find CommandLineOptionProcessor
                OptionProcessor processor = optionsIterator.next();
                if (processor instanceof OpenFromArguments) {
                    // check if we are running from command line
                    casePath = Paths.get(((OpenFromArguments) processor).getDefaultArg()).toAbsolutePath().toString();
                    break;
                }
            }

            if (casePath == null || casePath.isEmpty()) {
                LOGGER.log(Level.SEVERE, "No command line commands specified");
                System.err.println("No command line commands specified");
                return;
            }

            try {
                CommandLineOpenCaseManager.this.openCase(casePath);
                LOGGER.log(Level.INFO, "Opening case at " + casePath);
            } catch (CaseActionException ex) {
                LOGGER.log(Level.SEVERE, "Error opening case from command line ", ex);
                System.err.println("Error opening case ");
            }
        }

    }
}
