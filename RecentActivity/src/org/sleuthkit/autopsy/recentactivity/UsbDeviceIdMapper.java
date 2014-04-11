 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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
 * In order to update the USB database you must first copy it from
 * http://www.linux-usb.org/usb.ids into a text file named "USB_DATA".
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

/**
 * Loads a file that maps USB IDs to names of makes and models. Uses Linux USB info. 
 */
class UsbDeviceIdMapper {
    private static final Logger logger = Logger.getLogger(UsbDeviceIdMapper.class.getName());
    private HashMap<String, USBInfo> devices;
    private static final String DataFile = "USB_DATA.txt";

    public UsbDeviceIdMapper() {
        try {
            loadDeviceMap();
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Could not find file " + DataFile + ".", ex);
            devices = null;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unknown IO error occurred in method devices.", ex);
        }
    }
    
    /**
     * Parses the passed in device ID and returns info that includes make and model. 
     * 
     * @param dev String to parse (i.e.: Vid_XXXX&Pid_XXXX)
     * @return 
     */
    public USBInfo parseAndLookup(String dev) {
        String[] dtokens = dev.split("[_&]");
        String vID = dtokens[1];
        String pID;
        if (dtokens.length < 4 || dtokens[3].length() < 4) {
            pID = "0000";
        } else {
            pID = dtokens[3];
        }
        String key = vID + pID;
        key = key.toUpperCase();
        if (!devices.containsKey(key)) {
            return new USBInfo(null, null);
        } else {
            return devices.get(key);
        }
    }

    /**
     * Reads the local USB txt file and stores in map.
     * 
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private void loadDeviceMap() throws FileNotFoundException, IOException {
        devices = new HashMap<>();
        PlatformUtil.extractResourceToUserConfigDir(this.getClass(), DataFile, false);
        try (Scanner dat = new Scanner(new FileInputStream(new java.io.File(PlatformUtil.getUserConfigDirectory() + File.separator + "USB_DATA.txt")))) {
            /* Syntax of file: 
             *
             * # vendor  vendor_name
             * #	device  device_name				<-- single tab
             * #		interface  interface_name		<-- two tabs
             */
            String line = dat.nextLine();
            while (dat.hasNext()) {
                
                // comments
                if ((line.startsWith("#")) || (line.equals(""))) {
                    line = dat.nextLine();
                    continue;
                }
                
                // stop once we've hitten the part of the file that starts to talk about class types
                if (line.startsWith("C 00")) {
                    return;
                }
                
                String dvc = "";
                String[] tokens = line.split("[\\t\\s]+");
                String vID = tokens[0];
                for (int n = 1; n < tokens.length; n++) {
                    dvc += tokens[n] + " ";
                }
                
                // make an entry with just the vendor ID
                String pID = vID + "0000";
                pID = pID.toUpperCase();
                USBInfo info = new USBInfo(dvc, null);
                devices.put(pID, info);

                // parseAndLookup the later lines that have specific products
                line = dat.nextLine();
                if (line.startsWith("\t")) {
                    while (dat.hasNext() && line.startsWith("\t")) {
                        tokens = line.split("[\\t\\s]+");

                        // make key based on upper case version of vendor and product IDs
                        pID = vID + tokens[1];
                        pID = pID.toUpperCase();

                        String device = "";
                        line = dat.nextLine();
                        for (int n = 2; n < tokens.length; n++) {
                            device += tokens[n] + " ";
                        }

                        info = new USBInfo(dvc, device);

                        // store based on the previously generated key
                        devices.put(pID, info);
                    }
                }
            }
        }
    }

    /**
     * Stores the vendor information about a USB device 
     */
    public class USBInfo {
        private String vendor;
        private String product;

        private USBInfo(String vend, String prod) {
            vendor = vend;
            product = prod;
        }

        /**
         * Get Vendor (make) information
         * @return 
         */
        public String getVendor() {
            return vendor;
        }

        /**
         * Get product (model) information
         * @return 
         */
        public String getProduct() {
            return product;
        }

        @Override
        public String toString() {
            return vendor + product;
        }
    }
}