/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.encryptiondetection;

import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Volume;

/**
 * Addendum class for the Encryption Detection data source module to detect
 * Bitlocker volumes.
 */
final class BitlockerDetection {

    private static final int BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE = 0x54;
    private static final byte[] BITLOCKER_SIGNATURE_BYTES = {'-', 'F', 'V', 'E', '-', 'F', 'S', '-'};
    private static final int BITLOCKER_ADDRESS_SIGNATURE = 0x3;
    private static final int BITLOCKER_ADDRESS_SECTORS_PER_CLUSTER = 0xD;
    private static final int BITLOCKER_ADDRESS_RESERVED_CLUSTERS = 0xE;
    private static final int BITLOCKER_ADDRESS_FAT_COUNT = 0x10;
    private static final int BITLOCKER_ADDRESS_ROOT_ENTRIES = 0x11;
    private static final int BITLOCKER_ADDRESS_SECTORS = 0x13;
    private static final int BITLOCKER_ADDRESS_SECTORS_PER_FAT = 0x16;
    private static final int BITLOCKER_ADDRESS_LARGE_SECTORS = 0x20;
    
    /**
     * Private constructor to prevent instantiation.
     */
    private BitlockerDetection() {
    }

    /**
     * This method checks if the Volume input has been encrypted with Bitlocker.
     *
     * @param volume Volume to be checked.
     *
     * @return True if the Volume has been encrypted with Bitlocker.
     *
     * @throws TskCoreException If there is a failure reading from the
     *                          InputStream.
     */
    static boolean isBitlockerVolume(Volume volume) throws TskCoreException {
        /*
         * Logic in this method is based on
         * https://blogs.msdn.microsoft.com/si_team/2006/10/26/detecting-bitlocker/
         */

        boolean bitlockerVolume = false;

        byte[] bpbArray = new byte[BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE];
        volume.read(bpbArray, 0, BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE);

        boolean signatureMatches = true;
        for (int i = 0; i < BITLOCKER_SIGNATURE_BYTES.length; i++) {
            if (bpbArray[BITLOCKER_ADDRESS_SIGNATURE + i] != BITLOCKER_SIGNATURE_BYTES[i]) {
                signatureMatches = false;
                break;
            }
        }

        if (signatureMatches) {
            switch ((int) bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_CLUSTER]) {
                case 0x01:
                case 0x02:
                case 0x04:
                case 0x08:
                case 0x10:
                case 0x20:
                case 0x40:
                case 0x80:
                    short reservedClusters
                            = (short) ((bpbArray[BITLOCKER_ADDRESS_RESERVED_CLUSTERS] << 8)
                            | (bpbArray[BITLOCKER_ADDRESS_RESERVED_CLUSTERS + 1] & 0xFF));
                    byte fatCount
                            = bpbArray[BITLOCKER_ADDRESS_FAT_COUNT];
                    short rootEntries
                            = (short) ((bpbArray[BITLOCKER_ADDRESS_ROOT_ENTRIES] << 8)
                            | (bpbArray[BITLOCKER_ADDRESS_ROOT_ENTRIES + 1] & 0xFF));
                    short sectors
                            = (short) ((bpbArray[BITLOCKER_ADDRESS_SECTORS] << 8)
                            | (bpbArray[BITLOCKER_ADDRESS_SECTORS + 1] & 0xFF));
                    short sectorsPerFat
                            = (short) ((bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_FAT] << 8)
                            | (bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_FAT + 1] & 0xFF));
                    int largeSectors
                            = ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS] << 24)
                            | ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 1] & 0xFF) << 16)
                            | ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 2] & 0xFF) << 8)
                            | (bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 3] & 0xFF));

                    if (reservedClusters == 0 && fatCount == 0 && rootEntries == 0
                            && sectors == 0 && sectorsPerFat == 0 && largeSectors == 0) {
                        bitlockerVolume = true;
                    }
                    
                    break;
                    
                default:
                    // Invalid value. This is not a Bitlocker volume.
            }
        }

        return bitlockerVolume;
    }
}