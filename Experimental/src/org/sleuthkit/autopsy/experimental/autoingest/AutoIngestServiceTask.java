/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.openide.LifecycleManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestManager.AutoIngestManagerStartupException;

/**
 * This class handles incoming and outgoing communications for the auto ingest
 * service.
 */
public class AutoIngestServiceTask implements Runnable {
    
    private static final Logger LOGGER = Logger.getLogger(AutoIngestServiceTask.class.getName());

    @Override
    public void run() {
        AutoIngestManager manager = AutoIngestManager.getInstance();
        try {
            // Start the auto ingest manager automatically.
            manager.startUp();
            manager.scanInputDirsNow();
        } catch (AutoIngestManager.AutoIngestManagerStartupException ex) {
            LOGGER.log(Level.SEVERE, ex.getMessage());
        }
        
        int portNumber = 4150;

        try (
            ServerSocket serverSocket =
                new ServerSocket(portNumber);
            Socket clientSocket = serverSocket.accept();
            PrintWriter out =
                new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
        ) {
            String inputLine;
            String outputLine;
            boolean waitForCommands = true;
            while(waitForCommands && (inputLine = in.readLine()) != null) {
                // A command has been recieved, so now the service must respond
                // accordingly.
                switch(inputLine.toLowerCase()) {
                    case "start":
                        try {
                            manager.startUp();
                            manager.scanInputDirsNow();
                            out.println("Auto ingest manager started.");
                        } catch (AutoIngestManagerStartupException ex) {
                            out.println("Error while starting auto ingest manager: " + ex.getMessage());
                        }
                        break;
                        
                    case "scan":
                        manager.scanInputDirsNow();
                        out.println("Input directories scanned.");
                        break;
                        
                    case "shutdown":
                        waitForCommands = false;
                        manager.shutDown();
                        out.println("Auto ingest manager shut down.");
                        break;
                        
                    case "pause":
                        manager.pause();
                        out.println("Auto ingest paused.");
                        break;
                        
                    case "resume":
                        manager.resume();
                        out.println("Auto ingest resumed.");
                        break;
                        
                    case "getstate":
                        out.println("The current state is " + manager.getState() + ".");
                        break;
                        
                    case "getjobs":
                        List<AutoIngestJob> pendingJobs = new ArrayList<>(0);
                        List<AutoIngestJob> runningJobs = new ArrayList<>(0);
                        List<AutoIngestJob> completedJobs = new ArrayList<>(0);
                        manager.getJobs(pendingJobs, runningJobs, completedJobs);
                        
                        outputLine = String.format("\n\n[PENDING JOBS]  %d jobs...\n", pendingJobs.size());
                        for(AutoIngestJob job: pendingJobs) {
                            outputLine += String.format("%-14s  %-14s  %s\n", job.getManifest().getCaseName(), job.getNodeName(), job.getCaseDirectoryPath());
                        }
                        
                        outputLine += String.format("\n[RUNNING JOBS]  %d jobs...\n", runningJobs.size());
                        for(AutoIngestJob job: runningJobs) {
                            outputLine += String.format("%-14s  %-14s  %s\n", job.getManifest().getCaseName(), job.getNodeName(), job.getCaseDirectoryPath());
                        }
                        
                        outputLine += String.format("\n[COMPLETED JOBS]  %d jobs...\n", completedJobs.size());
                        for(AutoIngestJob job: completedJobs) {
                            outputLine += String.format("%-14s  %-14s  %s\n", job.getManifest().getCaseName(), job.getNodeName(), job.getCaseDirectoryPath());
                        }
                        
                        out.println(outputLine);
                        break;
                        
                    default:
                        out.println("Invalid command.");
                }
            }
        } catch(IOException ex) {
            LOGGER.log(Level.SEVERE, String.format(
                    "Exception caught when trying to listen on port %d or listening for a connection: %s",
                    portNumber, ex.getMessage()));
        }
        
        LifecycleManager.getDefault().exit();
    }
}