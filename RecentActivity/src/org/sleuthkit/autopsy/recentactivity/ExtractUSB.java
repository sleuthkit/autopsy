 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
public class ExtractUSB {

    private HashMap<String, USB_Info> devices;

    public USB_Info get(String dev) {
        String[] dtokens = dev.split("[_&]");
        String mID = dtokens[1];
        String pID;
        if (dtokens.length < 4 || dtokens[3].length() < 4) {
            pID = mID + "0000";
        } else {
            pID = mID + dtokens[3];
        }
        if (!devices.containsKey(pID)) {
            return new USB_Info("No such Device", null);
        } else {
            return devices.get(pID);
        }
    }

    public ExtractUSB() {
        try {
            Devices();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ExtractUSB.class.getName()).log(Level.SEVERE, null, ex);
            devices = null;
        } catch (IOException ex) {
            Logger.getLogger(ExtractUSB.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void Devices() throws FileNotFoundException, IOException {
        devices = new HashMap<String, USB_Info>();
        PlatformUtil.extractResourceToUserConfigDir(this.getClass(), "USB_DATA.txt");
        try (Scanner dat = new Scanner(new FileInputStream(new java.io.File(PlatformUtil.getUserConfigDirectory() + File.separator + "USB_DATA.txt")))) {
            String line = dat.nextLine();
            while (dat.hasNext()) {
                String dvc = "";
                if (!(line.startsWith("#") || (line.equals("")))) {
                    String[] tokens = line.split("[\\t\\s]+");
                    String vID = tokens[0];
                    for (int n = 1; n < tokens.length; n++) {
                        dvc += tokens[n] + " ";
                    }
                    String pID = vID + "0000";
                    USB_Info info = new USB_Info(dvc, null);
                    devices.put(pID, info);
                    line = dat.nextLine();
                    if (line.startsWith("\t")) {
                        while (dat.hasNext() && line.startsWith("\t")) {
                            tokens = line.split("[\\t\\s]+");
                            pID = vID + tokens[1];
                            String device = "";
                            line = dat.nextLine();
                            for (int n = 2; n < tokens.length; n++) {
                                device += tokens[n] + " ";
                            }
                            info = new USB_Info(dvc, device);
                            devices.put(pID, info);
                        }
                    }
                } else {
                    line = dat.nextLine();
                }
            }
        }
    }

    public class USB_Info {

        private String vendor;
        private String product;

        private USB_Info(String vend, String prod) {
            vendor = vend;
            product = prod;
        }

        public String get_Vendor() {
            return vendor;
        }

        public String get_Product() {
            return product;
        }
        public String toString(){
            return vendor + product;
        }
    }
}