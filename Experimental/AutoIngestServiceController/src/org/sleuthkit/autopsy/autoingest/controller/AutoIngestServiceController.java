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
package org.sleuthkit.autopsy.autoingest.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class AutoIngestServiceController {
    private static String hostName;
    private static int portNumber;
    private static String command;
    
    private static void printTitle() {
        System.out.println(
                "==================================================\n" +
                "Auto Ingest Service Controller 0.9.0\n" +
                "Copyright(c) 2017, Basis Technology Corp.\n" +
                "==================================================\n");
    }
    
    private static void printUsage() {
        System.out.println(
                "Usage 1: AutoIngestServiceController.exe <host name> <port number>\n" +
                "Usage 2: AutoIngestServiceController.exe <host name> <port number> <command>\n");
    }
    
    public static void main(String[] args) {
        printTitle();
        switch(args.length) {
            case 3:
                command = args[2];
                // Fall through
            case 2:
                portNumber = Integer.parseInt(args[1]);
                hostName = args[0];
                break;
                
            default:
                printUsage();
                System.exit(1);
        }
 
        try (
            Socket echoSocket = new Socket(hostName, portNumber);
            PrintWriter out =
                new PrintWriter(echoSocket.getOutputStream(), true);
            BufferedReader in =
                new BufferedReader(
                    new InputStreamReader(echoSocket.getInputStream()));
            BufferedReader stdIn =
                new BufferedReader(
                    new InputStreamReader(System.in))
        ) {
            if(command != null) {
                System.out.println("Client> " + command);
                out.println(command);
                System.out.println("Server> " + in.readLine());
            }
            if(command == null || !command.equalsIgnoreCase("shutdown")) {
                command = null;
                System.out.print("Client> ");
                while ((command = stdIn.readLine()) != null) {
                    if(command.equalsIgnoreCase("exit")) {
                        break;
                    }
                    
                    if(!command.isEmpty()) {
                        out.println(command);
                        char[] serverMessage = new char[16384];
                        for(int i=0; i < serverMessage.length; i++) {
                            serverMessage[i] = '\n';
                        }
                        int messageLength = in.read(serverMessage, 0, serverMessage.length - 1);
                        System.out.println("Server> " + String.copyValueOf(serverMessage, 0, messageLength - 1));
                        if(command.equalsIgnoreCase("shutdown")) {
                            break;
                        }
                    }
                    System.out.print("\nClient> ");
                }
            }
        } catch (UnknownHostException e) {
            System.err.println("Error: Don't know about host " + hostName);
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Error: Couldn't get I/O for the connection to " +
                hostName);
            System.exit(3);
        } 
    }
}
