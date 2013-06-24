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

/**
 * In order to update the USB database you must first copy it from a the internet
 * into a text file named "USB_DATA". Then remove all lines from below the entry
 * "ee38  Digital Storage Oscilloscope" and put them in another folder, for now 
 * labeled "OTHER_USB_DATA", this data is currently unused but may be used in the
 * future.  Then remove the whole line 
 * "	4004  Minolta Dimage Scan Elite II AF-2920 (2888)" and the line "#typo?"
 * above it.
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

    private HashMap<String, USBInfo> devices;

    public USBInfo get(String dev) {
        String[] dtokens = dev.split("[_&]");
        String mID = dtokens[1];
        String pID;
        if (dtokens.length < 4 || dtokens[3].length() < 4) {
            pID = mID + "0000";
        } else {
            pID = mID + dtokens[3];
        }
        if (!devices.containsKey(pID)) {
            return new USBInfo(null, null);
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
        devices = new HashMap<String, USBInfo>();
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
                    USBInfo info = new USBInfo(dvc, null);
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
                            info = new USBInfo(dvc, device);
                            devices.put(pID, info);
                        }
                    }
                } else {
                    line = dat.nextLine();
                }
            }
        }
    }

    public class USBInfo {

        private String vendor;
        private String product;

        private USBInfo(String vend, String prod) {
            vendor = vend;
            product = prod;
        }

        public String getVendor() {
            return vendor;
        }

        public String getProduct() {
            return product;
        }
        public String toString(){
            return vendor + product;
        }
    }
}