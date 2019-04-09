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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.Content;

/**
 * Class containing common methods concerning the Encryption Detection module.
 */
final class EncryptionDetectionTools {

    private static final double ONE_OVER_LOG2 = 1.4426950408889634073599246810019; // (1 / log(2))
    private static final int BYTE_OCCURENCES_BUFFER_SIZE = 256;
    static final double MINIMUM_ENTROPY_INPUT_RANGE_MIN = 6.0;
    static final double MINIMUM_ENTROPY_INPUT_RANGE_MAX = 8.0;
    static final int MINIMUM_FILE_SIZE_INPUT_RANGE_MIN = 1;

    @NbBundle.Messages({
        "EncryptionDetectionTools.errorMessage.minimumEntropyInput=Minimum entropy input must be a number between 6.0 and 8.0."
    })
    /**
     * Check if the minimum entropy setting is in the accepted range for this
     * module.
     */
    static void validateMinEntropyValue(double minimumEntropy) throws IngestModule.IngestModuleException {
        if (minimumEntropy < MINIMUM_ENTROPY_INPUT_RANGE_MIN || minimumEntropy > MINIMUM_ENTROPY_INPUT_RANGE_MAX) {
            throw new IngestModule.IngestModuleException(Bundle.EncryptionDetectionTools_errorMessage_minimumEntropyInput());
        }
    }

    @NbBundle.Messages({
        "EncryptionDetectionTools.errorMessage.minimumFileSizeInput=Minimum file size input must be an integer (in megabytes) of 1 or greater."
    })
    /**
     * Check if the minimum file size setting is in the accepted range for this
     * module.
     */
    static void validateMinFileSizeValue(int minimumFileSize) throws IngestModule.IngestModuleException {
        if (minimumFileSize < MINIMUM_FILE_SIZE_INPUT_RANGE_MIN) {
            throw new IngestModule.IngestModuleException(Bundle.EncryptionDetectionTools_errorMessage_minimumFileSizeInput());
        }
    }


    /**
     * Calculate the entropy of the content. The result is used to qualify the
     * content as possibly encrypted.
     *
     * @param content The content to be calculated against.
     * @param context The ingest job context for cancellation checks
     *
     * @return The entropy of the content.
     *
     * @throws ReadContentInputStreamException If there is a failure reading
     *                                         from the InputStream.
     * @throws IOException                     If there is a failure closing or
     *                                         reading from the InputStream.
     */
    static double calculateEntropy(Content content, IngestJobContext context) throws ReadContentInputStream.ReadContentInputStreamException, IOException {
        /*
         * Logic in this method is based on
         * https://github.com/willjasen/entropy/blob/master/entropy.java
         */

        InputStream in = null;
        BufferedInputStream bin = null;

        try {
            in = new ReadContentInputStream(content);
            bin = new BufferedInputStream(in);

            /*
             * Determine the number of times each byte value appears.
             */
            int[] byteOccurences = new int[BYTE_OCCURENCES_BUFFER_SIZE];
            int readByte;
            long bytesRead = 0;
            while ((readByte = bin.read()) != -1) {
                byteOccurences[readByte]++;
                
                // Do a cancellation check every 10,000 bytes
                bytesRead++;
                if (bytesRead % 10000 == 0) {
                    if (context.dataSourceIngestIsCancelled() || context.fileIngestIsCancelled()) {
                        return 0;
                    }
                }
            }

            /*
             * Calculate the entropy based on the byte occurence counts.
             */
            long dataLength = content.getSize() - 1;
            double entropyAccumulator = 0;
            for (int i = 0; i < BYTE_OCCURENCES_BUFFER_SIZE; i++) {
                if (byteOccurences[i] > 0) {
                    double byteProbability = (double) byteOccurences[i] / (double) dataLength;
                    entropyAccumulator += (byteProbability * Math.log(byteProbability) * ONE_OVER_LOG2);
                }
            }

            return -entropyAccumulator;

        } finally {
            if (in != null) {
                in.close();
            }
            if (bin != null) {
                bin.close();
            }
        }
    }
    
    /**
     * Private constructor for Encryption Detection Tools class.
     */
    private EncryptionDetectionTools() {
    }
}
