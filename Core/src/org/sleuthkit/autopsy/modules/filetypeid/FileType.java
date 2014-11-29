/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.Arrays;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a named file type characterized by a file signature.
 */
class FileType {

    private final String typeName;
    private final Signature signature;
    private final boolean alert;

    /**
     * Creates a representation of a named file type characterized by a file
     * signature.
     *
     * @param typeName The name of the file type.
     * @param signature The signature that characterizes the file type.
     * @param alert A flag indicating whether the user wishes to be alerted when
     * a file matching this type is encountered.
     */
    FileType(String typeName, Signature signature, boolean alert) {
        this.typeName = typeName;
        this.signature = signature;
        this.alert = alert;
    }

    /**
     * Gets the name associated with this file type.
     *
     * @return The type name.
     */
    String getTypeName() {
        return this.typeName;
    }

    /**
     * Gets the signature associated with this file type.
     *
     * @return The file signature.
     */
    Signature getSignature() {
        return this.signature;
    }

    /**
     * Determines whether or not a given file is an instance of this file type.
     *
     * @param file The file to test
     * @return True or false.
     */
    boolean matches(AbstractFile file) {
        return this.signature.containedIn(file);
    }

    /**
     * Indicates whether or not an alert is desired if a file of this type is
     * encountered.
     *
     * @return True or false.
     */
    boolean alertOnMatch() {
        return this.alert;
    }

    /**
     * Represents a file signature consisting of a sequence of bytes at a
     * specific offset within a file.
     */
    static class Signature {

        /**
         * The way the signature byte sequence should be interpreted.
         */
        enum Type {

            RAW, ASCII
        };

        private final byte[] signatureBytes;
        private final long offset;
        private final Type type;

        /**
         * Creates a representation of a file signature consisting of a sequence
         * of bytes at a specific offset within a file.
         *
         * @param signatureBytes The signature bytes
         * @param offset The offset of the signature bytes.
         * @param type The interpretation of the signature bytes (e.g., raw
         * bytes, an ASCII string).
         */
        Signature(byte[] signatureBytes, long offset, Type type) {
            this.signatureBytes = signatureBytes;
            this.offset = offset;
            this.type = type;
        }

        /**
         * Gets the byte sequence of the signature.
         *
         * @return The byte sequence as an array of bytes.
         */
        byte[] getSignatureBytes() {
            return Arrays.copyOf(this.signatureBytes, this.signatureBytes.length);
        }

        /**
         * Gets the offset of the signature.
         *
         * @return The offset.
         */
        long getOffset() {
            return this.offset;
        }

        /**
         * Gets the interpretation of the byte sequence for the signature.
         *
         * @return The signature type.
         */
        Type getType() {
            return this.type;
        }

        /**
         * Determines whether or not the signature is contained within a given
         * file.
         *
         * @param file The file to test
         * @return True or false.
         */
        boolean containedIn(AbstractFile file) {
            try {
                byte[] buffer = new byte[this.signatureBytes.length];
                int bytesRead = file.read(buffer, offset, this.signatureBytes.length);
                return ((bytesRead == this.signatureBytes.length) && (Arrays.equals(buffer, this.signatureBytes)));
            } catch (TskCoreException ex) {
                return false;
            }
        }
    }

}
