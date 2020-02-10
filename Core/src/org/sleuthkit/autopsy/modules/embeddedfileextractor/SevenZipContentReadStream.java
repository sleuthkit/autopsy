/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.IOException;
import java.util.logging.Level;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.SevenZipException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Adapter from ReadContentInputStream to net.sf.sevenzipjbinding.IInStream
 * stream interface
 */
class SevenZipContentReadStream implements IInStream {

    private ReadContentInputStream wrapped;
    private long length;

    private static final Logger logger = Logger.getLogger(SevenZipContentReadStream.class.getName());

    public SevenZipContentReadStream(ReadContentInputStream wrapped) {
        this.wrapped = wrapped;
        this.length = wrapped.getLength();
    }

    @Override
    public long seek(long offset, int origin) throws SevenZipException {
        long curPosition = wrapped.getCurPosition();
        long newPosition = curPosition;
        switch (origin) {
            case SEEK_CUR:
                newPosition = wrapped.seek(curPosition + offset);
                break;
            case SEEK_END:
                //(offset <= 0) offset is set from EOF
                newPosition = wrapped.seek(length + offset);
                break;
            case SEEK_SET:
                newPosition = wrapped.seek(offset);
                break;
            default:
                throw new IllegalArgumentException(
                        NbBundle.getMessage(this.getClass(), "SevenZipContentReadStream.seek.exception.invalidOrigin",
                                origin));
        }

        return newPosition;

    }

    @Override
    public int read(byte[] bytes) throws SevenZipException {
    //Reads at least 1 and maximum data.length from the in-stream. 
        //If data.length == 0 0 should be returned. 
        //If data.length != 0, then return value 0 indicates end-of-stream (EOF). This means no more bytes can be read from the stream.
        //This function is allowed to read less than number of remaining bytes in stream and less then data.length. 
        if (bytes.length == 0) {
            return 0;
        }

        try {
            int readBytes = wrapped.read(bytes);
            if (readBytes < 1) {
                return 0;
            }
            return readBytes;

        } catch (IOException ex) {
            // This is only a warning because the file may be deleted or otherwise corrupt
            String msg = NbBundle.getMessage(this.getClass(), "SevenZipContentReadStream.read.exception.errReadStream");
            logger.log(Level.WARNING, msg, ex);
            throw new SevenZipException(msg, ex);
        }
    }

    /**
     * Close the stream
     *
     * @throws IOException
     */
    public void close() throws IOException {
        wrapped.close();
    }
}
