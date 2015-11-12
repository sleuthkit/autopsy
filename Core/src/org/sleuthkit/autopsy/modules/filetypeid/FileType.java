/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2015 Basis Technology Corp.
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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a file type characterized by a file signature.
 * <p>
 * Thread-safe (immutable).
 */
class FileType {

    private final String mimeType;
    private final Signature signature;
    private final String interestingFilesSetName;
    private final boolean alert;

    /**
     * Creates a representation of a file type characterized by a file
     * signature.
     *
     * @param mimeType     The mime type to associate with this file type.
     * @param signature    The signature that characterizes this file type.
     * @param filesSetName The name of an interesting files set that includes
     *                     files of this type, may be the empty string.
     * @param alert        Whether the user wishes to be alerted when a file
     *                     matching this type is encountered.
     */
    FileType(String mimeType, final Signature signature, String filesSetName, boolean alert) {
        this.mimeType = mimeType;
        this.signature = new Signature(signature.getSignatureBytes(), signature.getOffset(), signature.getType(), signature.isRelativeToStart());
        this.interestingFilesSetName = filesSetName;
        this.alert = alert;
    }

    /**
     * Gets the MIME type associated with this file type.
     *
     * @return The MIME type.
     */
    String getMimeType() {
        return mimeType;
    }

    /**
     * Gets the signature associated with this file type.
     *
     * @return The signature.
     */
    Signature getSignature() {
        return new Signature(signature.getSignatureBytes(), signature.getOffset(), signature.getType(), signature.isRelativeToStart());
    }

    /**
     * Determines whether or not a file is an instance of this file type.
     *
     * @param file The file to test.
     *
     * @return True or false.
     */
    boolean matches(final AbstractFile file) {
        return signature.containedIn(file);
    }

    /**
     * Indicates whether or not an alert is desired if a file of this type is
     * encountered.
     *
     * @return True or false.
     */
    boolean alertOnMatch() {
        return alert;
    }

    /**
     * Gets the name of an interesting files set that includes files of this
     * type.
     *
     * @return The interesting files set name, possibly empty.
     */
    String getFilesSetName() {
        return interestingFilesSetName;
    }
    
    @Override
    public String toString() {
        return this.mimeType;
    }
    
    @Override
    public boolean equals(Object other) {
        if(other != null && other instanceof FileType) {
            FileType that = (FileType) other;
            if(this.getMimeType().equals(that.getMimeType()) && this.getSignature().equals(that.getSignature()))
                return true;
        }        
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.mimeType);
        hash = 67 * hash + Objects.hashCode(this.signature);
        return hash;
    }

    /**
     * A file signature consisting of a sequence of bytes at a specific offset
     * within a file.
     * <p>
     * Thread-safe (immutable).
     */
    static class Signature {

        private static final Logger logger = Logger.getLogger(Signature.class.getName());

        /**
         * The way the signature byte sequence should be interpreted.
         */
        enum Type {
            RAW, ASCII
        };

        private final byte[] signatureBytes;
        private final long offset;
        private final Type type;
        private final boolean isRelativeToStart;

        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file.
         *
         * @param signatureBytes The signature bytes.
         * @param offset         The offset of the signature bytes.
         * @param type           The type of data in the byte array. Impacts
         *                       how it is displayed to the user in the UI. 
         */
        Signature(final byte[] signatureBytes, long offset, Type type) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = type;
            this.isRelativeToStart = true;
        }
        
        /**
         * Creates a file signature consisting of an ASCII string at a
         * specific offset within a file.
         *
         * @param signatureString The ASCII string
         * @param offset         The offset of the signature bytes.
         */
        Signature(String signatureString, long offset) {
            this.signatureBytes = signatureString.getBytes(StandardCharsets.US_ASCII);
            this.offset = offset;
            this.type = Type.ASCII;
            this.isRelativeToStart = true;
        }
        
        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file.  If bytes correspond to an ASCII
         * string, use one of the other constructors so that the string is 
         * displayed to the user instead of the raw bytes. 
         *
         * @param signatureBytes The signature bytes.
         * @param offset         The offset of the signature bytes.
         */
        Signature(final byte[] signatureBytes, long offset) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = Type.RAW;
            this.isRelativeToStart = true;
        }
        
        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file.
         *
         * @param signatureBytes        The signature bytes.
         * @param offset                The offset of the signature bytes.
         * @param type                  The type of data in the byte array. Impacts
         *                              how it is displayed to the user in the UI.
         * @param isRelativeToStart     Determines whether this signature is relative to start.
         */
        Signature(final byte[] signatureBytes, long offset, Type type, boolean isRelativeToStart) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = type;
            this.isRelativeToStart = isRelativeToStart;
        }
        
        /**
         * Creates a file signature consisting of an ASCII string at a
         * specific offset within a file.
         *
         * @param signatureString       The ASCII string
         * @param offset                The offset of the signature bytes.
         * @param isRelativeToStart     Determines whether this signature is relative to start.
         */
        Signature(String signatureString, long offset, boolean isRelativeToStart) {
            this.signatureBytes = signatureString.getBytes(StandardCharsets.US_ASCII);
            this.offset = offset;
            this.type = Type.ASCII;
            this.isRelativeToStart = isRelativeToStart;
        }
        
        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file.  If bytes correspond to an ASCII
         * string, use one of the other constructors so that the string is 
         * displayed to the user instead of the raw bytes. 
         *
         * @param signatureBytes        The signature bytes.
         * @param offset                The offset of the signature bytes.
         * @param isRelativeToStart     Determines whether this signature is relative to start.
         */
        Signature(final byte[] signatureBytes, long offset, boolean isRelativeToStart) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = Type.RAW;
            this.isRelativeToStart = isRelativeToStart;
        }

        /**
         * Gets the byte sequence of the signature.
         *
         * @return The byte sequence as an array of bytes.
         */
        byte[] getSignatureBytes() {
            return Arrays.copyOf(signatureBytes, signatureBytes.length);
        }

        /**
         * Gets the offset of the signature.
         *
         * @return The offset.
         */
        long getOffset() {
            return offset;
        }

        /**
         * Gets the interpretation of the byte sequence for the signature.
         *
         * @return The signature type.
         */
        Type getType() {
            return type;
        }
        
        boolean isRelativeToStart() {
            return isRelativeToStart;
        }

        /**
         * Determines whether or not the signature is contained within a given
         * file.
         *
         * @param file The file to test
         *
         * @return True or false.
         */
        boolean containedIn(final AbstractFile file) {
            if(offset >= file.getSize()) {
                return false; // File is too small, offset lies outside file.
            }
            long actualOffset = offset;
            if(!isRelativeToStart)
                actualOffset = file.getSize() - 1 - offset;
            if (file.getSize() < (actualOffset + signatureBytes.length)) {
                return false; /// too small, can't contain this signature
            }
            try {
                byte[] buffer = new byte[signatureBytes.length];
                int bytesRead = file.read(buffer, actualOffset, signatureBytes.length);
                return ((bytesRead == signatureBytes.length) && (Arrays.equals(buffer, signatureBytes)));
            } catch (TskCoreException ex) {
                /**
                 * This exception is swallowed rather than propagated because
                 * files in images are not always consistent with their file
                 * system meta data making for read errors.
                 */
                Signature.logger.log(Level.WARNING, "Error reading from file with objId = " + file.getId(), ex); //NON-NLS
                return false;
            }
        }
        
        @Override
        public boolean equals(Object other) {
            if (other != null && other instanceof Signature) {
                Signature that = (Signature) other;
                if(Arrays.equals(this.getSignatureBytes(), that.getSignatureBytes()) 
                        && this.getOffset() == that.getOffset()
                        && this.getType().equals(that.getType()))
                    return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + Arrays.hashCode(this.signatureBytes);
            hash = 97 * hash + (int) (this.offset ^ (this.offset >>> 32));
            hash = 97 * hash + Objects.hashCode(this.type);
            return hash;
        }
    }

}
