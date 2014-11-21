/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * RJCTODO
 */
class UserDefinedFileTypeDetector {

    private final List<SignatureMatcher> matchers;
    private String detectedType;

    static UserDefinedFileTypeDetector createDetector(String sigFilePath) {
        UserDefinedFileTypeDetector detector = new UserDefinedFileTypeDetector();
        detector.loadSignatures(sigFilePath);
        return detector;
    }

    /**
     * RJCTODO
     *
     * @param sigFilePath
     */
    UserDefinedFileTypeDetector() {
        this.matchers = new ArrayList<>();
    }

    /**
     * RJCTODO
     */
    private void loadSignatures(String sigFilePath) {
        // RJCTODO: Load signature file, creating 
    }

    /**
     * 
     * @param file
     * @return 
     */
    boolean detect(AbstractFile file) {
        for (SignatureMatcher matcher : this.matchers) {
            if (matcher.matched(file)) {
                this.detectedType = matcher.getMatchType();
                return true;
            }
        }
        this.detectedType = ""; // RJCTODO: Wrap this stuff in a class
        return false;
    }

    /**
     * RJCTODO
     * @return 
     */
    String getDetectedType() {
        // RJCTODO
       return ""; 
    }
    
    /**
     *
     */
    private static class SignatureMatcher {

        private final List<Matcher> matchers;
        private final String matchType;

        /**
         * RJCTODO
         *
         * @param matchType
         */
        SignatureMatcher(String matchType) {
            this.matchType = matchType;
            this.matchers = new ArrayList<>();
        }

        /**
         * RJCTODO
         *
         * @param file
         * @return
         */
        boolean matched(AbstractFile file) {
            for (Matcher matcher : this.matchers) {
                if (!matcher.matched(file)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * RJCTODO
         *
         * @return
         */
        String getMatchType() {
            return this.matchType;
        }

    }

    private static interface Matcher {

        /**
         * RJCTODO
         *
         * @param file
         * @return
         */
        boolean matched(AbstractFile file);

    }

    /**
     * A signature matcher that looks for a sequence of bytes at a specified
     * offset.
     */
    private static class ByteMatcher implements Matcher {

        private final long offset;
        private final byte[] signature;
        private final byte[] buffer;

        private ByteMatcher(long offset, byte[] signature) {
            this.offset = offset;
            this.signature = signature;
            this.buffer = new byte[signature.length];
        }

        /**
         * @inheritDoc
         */
        @Override
        public boolean matched(AbstractFile file) {
            try {
                // RJCTODO: Confirm this logic
                int bytesRead = file.read(this.buffer, offset, buffer.length);
                return bytesRead == buffer.length ? Arrays.equals(this.buffer, this.signature) : false;
            } catch (TskCoreException ex) {
                // RJCTODO
                return false;
            }
        }

    }

}
