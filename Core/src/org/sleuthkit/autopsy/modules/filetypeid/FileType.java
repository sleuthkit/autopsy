/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import org.apache.commons.codec.binary.Hex;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a file type characterized by file signatures.
 * <p>
 * Thread-safe (immutable).
 */
class FileType implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String mimeType;
    private final List<Signature> signatures;
    private final boolean createInterestingFileHit;
    private final String interestingFilesSetName;

    /**
     * Creates a representation of a file type characterized by file signatures.
     *
     * @param mimeType   The mime type to associate with this file type.
     * @param signatures The signatures that characterize this file type.
     *
     * @throws IllegalArgumentException If an empty list of signatures is given.
     */
    FileType(String mimeType, List<Signature> signatures) throws IllegalArgumentException {
        this(mimeType, signatures, false, "");
    }

    /**
     * Creates a representation of a file type characterized by file signatures.
     *
     * @param mimeType   The mime type to associate with this file type.
     * @param signatures The signatures that characterize this file type.
     * @param createInterestingFileHit  Create interesting file hit for file type?
     * @param setName    Name of the interesting file set in which to create hit.
     *
     * @throws IllegalArgumentException If an empty list of signatures is given.
     */
    FileType(String mimeType, List<Signature> signatures, boolean createInterestingFileHit, String setName) throws IllegalArgumentException {
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("Must have at least one signature.");
        }
        this.mimeType = mimeType;
        this.signatures = new ArrayList<>(signatures);
        this.createInterestingFileHit = createInterestingFileHit;
        this.interestingFilesSetName = setName;
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
     * Gets the name of the interesting files set associated with this file
     * type.
     *
     * @return The interesting files set name.
     */
    String getInterestingFilesSetName() {
        return interestingFilesSetName;
    }

    /**
     * Should an interesting files hit be created for this file type?
     *
     * @return true if an interesting files hit should be created, otherwise
     * false
     */
    boolean shouldCreateInterestingFileHit() {
        return createInterestingFileHit;
    }

    /**
     * Gets the signatures associated with this file type.
     *
     * @return The signatures.
     */
    List<Signature> getSignatures() {
        return Collections.unmodifiableList(this.signatures);
    }

    /**
     * Adds a signature to the file type
     *
     * @param sig The signature to add
     */
    void addSignature(Signature sig) {
        this.signatures.add(sig);
    }

    /**
     * Determines whether or not a file is an instance of this file type.
     *
     * @param file The file to test.
     *
     * @return True or false.
     */
    boolean matches(final AbstractFile file, byte[] startOfFileBuffer, int bufLen) {
        for (Signature sig : this.signatures) {
            if (!sig.containedIn(file, startOfFileBuffer, bufLen)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return this.mimeType;
    }

    @Override
    public boolean equals(Object other) {
        if (other != null && other instanceof FileType) {
            FileType that = (FileType) other;
            if (this.getMimeType().equals(that.getMimeType()) && this.getSignatures().equals(that.getSignatures())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.mimeType);
        hash = 67 * hash + Objects.hashCode(this.signatures);
        return hash;
    }

    /**
     * A file signature consisting of a sequence of bytes at a specific offset
     * within a file.
     * <p>
     * Thread-safe (immutable).
     */
    static class Signature implements Serializable {

        private static final long serialVersionUID = 1L;
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
         * @param type           The type of data in the byte array. Impacts how
         *                       it is displayed to the user in the UI.
         */
        Signature(final byte[] signatureBytes, long offset, Type type) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = type;
            this.isRelativeToStart = true;
        }

        /**
         * Creates a file signature consisting of an ASCII string at a specific
         * offset within a file.
         *
         * @param signatureString The ASCII string
         * @param offset          The offset of the signature bytes.
         */
        Signature(String signatureString, long offset) {
            this.signatureBytes = signatureString.getBytes(StandardCharsets.US_ASCII);
            this.offset = offset;
            this.type = Type.ASCII;
            this.isRelativeToStart = true;
        }

        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file. If bytes correspond to an ASCII
         * string, use one of the other constructors so that the string is
         * displayed to the user instead of the raw bytes.
         *
         * @param signatureBytes The signatures bytes.
         * @param offset         The offset of the signatures bytes.
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
         * @param signatureBytes    The signature bytes.
         * @param offset            The offset of the signature bytes.
         * @param type              The type of data in the byte array. Impacts
         *                          how it is displayed to the user in the UI.
         * @param isRelativeToStart Determines whether this signature is
         *                          relative to start.
         */
        Signature(final byte[] signatureBytes, long offset, Type type, boolean isRelativeToStart) {
            this.signatureBytes = Arrays.copyOf(signatureBytes, signatureBytes.length);
            this.offset = offset;
            this.type = type;
            this.isRelativeToStart = isRelativeToStart;
        }

        /**
         * Creates a file signature consisting of an ASCII string at a specific
         * offset within a file.
         *
         * @param signatureString   The ASCII string
         * @param offset            The offset of the signature bytes.
         * @param isRelativeToStart Determines whether this signature is
         *                          relative to start.
         */
        Signature(String signatureString, long offset, boolean isRelativeToStart) {
            this.signatureBytes = signatureString.getBytes(StandardCharsets.US_ASCII);
            this.offset = offset;
            this.type = Type.ASCII;
            this.isRelativeToStart = isRelativeToStart;
        }

        /**
         * Creates a file signature consisting of a sequence of bytes at a
         * specific offset within a file. If bytes correspond to an ASCII
         * string, use one of the other constructors so that the string is
         * displayed to the user instead of the raw bytes.
         *
         * @param signatureBytes    The signature bytes.
         * @param offset            The offset of the signature bytes.
         * @param isRelativeToStart Determines whether this signature is
         *                          relative to start.
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
        boolean containedIn(final AbstractFile file, byte[] startOfFileBuffer, int bufLen) {
            if (offset >= file.getSize()) {
                return false; // File is too small, offset lies outside file.
            }
            long actualOffset = offset;
            if (!isRelativeToStart) {
                actualOffset = file.getSize() - 1 - offset;
            }
            if (file.getSize() < (actualOffset + signatureBytes.length)) {
                return false; /// too small, can't contain this signature
            }
            try {
                byte[] buffer = new byte[signatureBytes.length];
                int bytesRead;
                if (actualOffset + signatureBytes.length < bufLen) {
                    // The signature is contained in the buffer we've already read, so
                    // just copy the appropriate section.
                    for (int i = 0; i < signatureBytes.length;i++) {
                        buffer[i] = startOfFileBuffer[(int)actualOffset + i];
                    }
                    bytesRead = signatureBytes.length;
                } else {
                    bytesRead = file.read(buffer, actualOffset, signatureBytes.length);
                }
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
                if (Arrays.equals(this.getSignatureBytes(), that.getSignatureBytes())
                        && this.getOffset() == that.getOffset()
                        && this.getType().equals(that.getType())) {
                    return true;
                }
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

        @Override
        public String toString() {
            String signatureBytesString;
            if (Signature.Type.RAW == this.getType()) {
                signatureBytesString = Hex.encodeHexString(this.getSignatureBytes());
                signatureBytesString = "0x" + signatureBytesString;
            } else {
                try {
                    signatureBytesString = new String(this.getSignatureBytes(), "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                            ex.getLocalizedMessage(),
                            Bundle.AddFileTypeSignaturePanel_signatureStringFail_text(),
                            JOptionPane.ERROR_MESSAGE);
                    signatureBytesString = "";
                }
            }
            String startOrEnd;
            if (this.isRelativeToStart) {
                startOrEnd = "start";
            } else {
                startOrEnd = "end";
            }
            return signatureBytesString + ", " + offset + " bytes from " + startOrEnd;

        }
    }

}
