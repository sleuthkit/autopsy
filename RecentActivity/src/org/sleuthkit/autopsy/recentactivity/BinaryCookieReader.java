/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.recentactivity.BinaryCookieReader.Cookie;

/**
 * The binary cookie reader encapsulates all the knowledge of how to read the
 * mac .binarycookie files into one class.
 *
 * The binarycookie file has a header which describes how many pages of cookies
 * and where they are located. Each cookie page has a header and a list of
 * cookies.
 *
 */
final class BinaryCookieReader implements Iterable<Cookie> {

    private static final Logger LOG = Logger.getLogger(BinaryCookieReader.class.getName());
    private static final int MAGIC_SIZE = 4;
    private static final int SIZEOF_INT_BYTES = 4;
    private static final int PAGE_HEADER_VALUE = 256;

    private static final String COOKIE_MAGIC = "cook"; //NON-NLS

    private static final int MAC_EPOC_FIX = 978307200;

    private final int[] pageSizeArray;
    private final File cookieFile;

    /**
     * The binary cookie reader encapsulates all the knowledge of how to read
     * the mac .binarycookie files into one class.
     *
     */
    private BinaryCookieReader(File cookieFile, int[] pageSizeArray) {
        this.cookieFile = cookieFile;
        this.pageSizeArray = pageSizeArray.clone();
    }

    /**
     * initalizeReader opens the given file, reads the header and checks that
     * the file is a binarycookie file. This function does not keep the file
     * open.
     *
     * @param cookieFile binarycookie file
     *
     * @return An instance of the reader
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static BinaryCookieReader initalizeReader(File cookieFile) throws FileNotFoundException, IOException {
        BinaryCookieReader reader = null;
        try (DataInputStream dataStream = new DataInputStream(new FileInputStream(cookieFile))) {

            byte[] magic = new byte[MAGIC_SIZE];
            if (dataStream.read(magic) != MAGIC_SIZE) {
                throw new IOException("Failed to read header, invalid file size (" + cookieFile.getName() + ")"); //NON-NLS
            }

            if (!(new String(magic)).equals(COOKIE_MAGIC)) {
                throw new IOException(cookieFile.getName() + " is not a cookie file"); //NON-NLS
            }

            int[] sizeArray = null;
            int pageCount = dataStream.readInt();
            if (pageCount != 0) {
                sizeArray = new int[pageCount];

                for (int cnt = 0; cnt < pageCount; cnt++) {
                    sizeArray[cnt] = dataStream.readInt();
                }

                LOG.log(Level.INFO, "No cookies found in {0}", cookieFile.getName()); //NON-NLS
            }

            reader = new BinaryCookieReader(cookieFile, sizeArray);
        }

        return reader;
    }

    /**
     * Creates and returns a instance of CookiePageIterator.
     *
     * @return CookiePageIterator
     */
    @Override
    public Iterator<Cookie> iterator() {
        return new CookiePageIterator();
    }

    /**
     * The cookiePageIterator iterates the binarycookie file by page.
     */
    private class CookiePageIterator implements Iterator<Cookie> {

        int pageIndex = 0;
        CookiePage currentPage = null;
        Iterator<Cookie> currentIterator = null;
        DataInputStream dataStream = null;

        /**
         * The cookiePageIterator iterates the binarycookie file by page.
         */
        CookiePageIterator() {
            if (pageSizeArray == null || pageSizeArray.length == 0) {
                return;
            }

            try {
                dataStream = new DataInputStream(new FileInputStream(cookieFile));
                // skip to the first page
                dataStream.skipBytes((2 * SIZEOF_INT_BYTES) + (pageSizeArray.length * SIZEOF_INT_BYTES));
            } catch (IOException ex) {

                String errorMessage = String.format("An error occurred creating an input stream for %s", cookieFile.getName());
                LOG.log(Level.WARNING, errorMessage, ex); //NON-NLS
                closeStream(); // Just incase the error was from skip
            }
        }

        /**
         * Returns true if there are more cookies in the binarycookie file.
         *
         * @return True if there are more cookies
         */
        @Override
        public boolean hasNext() {

            if (dataStream == null) {
                return false;
            }

            if (currentIterator == null || !currentIterator.hasNext()) {
                try {

                    if (pageIndex < pageSizeArray.length) {
                        byte[] nextPage = new byte[pageSizeArray[pageIndex]];
                        dataStream.read(nextPage);

                        currentPage = new CookiePage(nextPage);
                        currentIterator = currentPage.iterator();
                    } else {
                        closeStream();
                        return false;
                    }

                    pageIndex++;
                } catch (IOException ex) {
                    closeStream();
                    String errorMessage = String.format("A read error occured for file %s (pageIndex = %d)", cookieFile.getName(), pageIndex);
                    LOG.log(Level.WARNING, errorMessage, ex); //NON-NLS
                    return false;
                }
            }

            return currentIterator.hasNext();
        }

        /**
         * Get the next cookie from the current CookieIterator.
         *
         * @return The next cookie
         */
        @Override
        public Cookie next() {
            // Just in case someone uses next without hasNext, this check will
            // make sure there are more elements and that we iterate properly 
            // through the pages.
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentIterator.next();
        }

        /**
         * Close the DataInputStream
         */
        private void closeStream() {
            if (dataStream != null) {
                try {
                    dataStream.close();
                    dataStream = null;
                } catch (IOException ex) {
                    String errorMessage = String.format("An error occurred trying to close stream for file %s", cookieFile.getName());
                    LOG.log(Level.WARNING, errorMessage, ex); //NON-NLS
                }
            }
        }
    }

    /**
     * Wrapper class for an instance of a CookiePage in the binarycookie file.
     */
    private class CookiePage implements Iterable<Cookie> {

        int[] cookieOffSets;
        ByteBuffer pageBuffer;

        /**
         * Setup the CookiePage object. Calidates that the page bytes are in the
         * correct format by checking for the header value of 0x0100.
         *
         * @param page byte array representing a cookie page
         *
         * @throws IOException
         */
        CookiePage(byte[] page) throws IOException {
            if (page == null || page.length == 0) {
                throw new IllegalArgumentException("Invalid value for page passed to CookiePage constructor"); //NON-NLS
            }

            pageBuffer = ByteBuffer.wrap(page);

            if (pageBuffer.getInt() != PAGE_HEADER_VALUE) {
                pageBuffer = null;
                throw new IOException("Invalid file format, bad page head value found"); //NON-NLS
            }

            pageBuffer.order(ByteOrder.LITTLE_ENDIAN);
            int count = pageBuffer.getInt();
            cookieOffSets = new int[count];

            for (int cnt = 0; cnt < count; cnt++) {
                cookieOffSets[cnt] = pageBuffer.getInt();
            }

            pageBuffer.getInt(); // All 0, not needed
        }

        /**
         * Returns an instance of a CookieIterator.
         *
         * @return CookieIterator
         */
        @Override
        public Iterator<Cookie> iterator() {
            return new CookieIterator();
        }

        /**
         * Implements Iterator to iterate over the cookies in the page.
         */
        private class CookieIterator implements Iterator<Cookie> {

            int index = 0;

            /**
             * Checks to see if there are more cookies.
             *
             * @return True if there are more cookies, false if there are not
             */
            @Override
            public boolean hasNext() {
                if (pageBuffer == null) {
                    return false;
                }

                return index < cookieOffSets.length;
            }

            /**
             * Gets the next cookie from the page.
             *
             * @return Next cookie
             */
            @Override
            public Cookie next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                int offset = cookieOffSets[index];
                int size = pageBuffer.getInt(offset);
                byte[] cookieBytes = new byte[size];
                pageBuffer.get(cookieBytes, 0, size);
                index++;

                return new Cookie(cookieBytes);
            }
        }
    }

    /**
     * Represents an instance of a cookie from the binarycookie file.
     */
    public class Cookie {

        private final static int COOKIE_HEAD_SKIP = 16;

        private final double expirationDate;
        private final double creationDate;

        private final String name;
        private final String url;
        private final String path;
        private final String value;

        /**
         * Creates a cookie object from the given array of bytes.
         *
         * @param cookieBytes Byte array for the cookie
         */
        protected Cookie(byte[] cookieBytes) {
            if (cookieBytes == null || cookieBytes.length == 0) {
                throw new IllegalArgumentException("Invalid value for cookieBytes passed to Cookie constructor"); //NON-NLS
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(cookieBytes);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            // Skip past the four int values that we are not interested in
            byteBuffer.position(byteBuffer.position() + COOKIE_HEAD_SKIP);

            int urlOffset = byteBuffer.getInt();
            int nameOffset = byteBuffer.getInt();
            int pathOffset = byteBuffer.getInt();
            int valueOffset = byteBuffer.getInt();
            byteBuffer.getLong(); // 8 bytes of not needed

            expirationDate = byteBuffer.getDouble();
            creationDate = byteBuffer.getDouble();

            url = decodeString(cookieBytes, urlOffset);
            name = decodeString(cookieBytes, nameOffset);
            path = decodeString(cookieBytes, pathOffset);
            value = decodeString(cookieBytes, valueOffset);
        }

        /**
         * Returns the expiration date of the cookie represented by this cookie
         * object.
         *
         * @return Cookie expiration date in milliseconds with java epoch
         */
        public final Long getExpirationDate() {
            return ((long) expirationDate) + MAC_EPOC_FIX;
        }

        /**
         * Returns the creation date of the cookie represented by this cookie
         * object.
         *
         * @return Cookie creation date in milliseconds with java epoch
         */
        public final Long getCreationDate() {
            return ((long) creationDate) + MAC_EPOC_FIX;
        }

        /**
         * Returns the url of the cookie represented by this cookie object.
         *
         * @return the cookie URL
         */
        public final String getURL() {
            return url;
        }

        /**
         * Returns the name of the cookie represented by this cookie object.
         *
         * @return The cookie name
         */
        public final String getName() {
            return name;
        }

        /**
         * Returns the path of the cookie represented by this cookie object.
         *
         * @return The cookie path
         */
        public final String getPath() {
            return path;
        }

        /**
         * Returns the value of the cookie represented by this cookie object.
         *
         * @return The cookie value
         */
        public final String getValue() {
            return value;
        }

        /**
         * Creates an ascii string from the bytes in byteArray starting at
         * offset ending at the first null terminator found.
         *
         * @param byteArray Array of bytes
         * @param offset    starting offset in the array
         *
         * @return String with bytes converted to ascii
         */
        private String decodeString(byte[] byteArray, int offset) {
            byte[] stringBytes = new byte[byteArray.length - offset];
            for (int index = 0; index < stringBytes.length; index++) {
                byte nibble = byteArray[offset + index];
                if (nibble != '\0') { //NON-NLS
                    stringBytes[index] = nibble;
                } else {
                    break;
                }
            }

            return new String(stringBytes);
        }
    }
}
