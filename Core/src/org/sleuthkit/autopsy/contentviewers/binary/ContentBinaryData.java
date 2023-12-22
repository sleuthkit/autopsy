/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.contentviewers.binary;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.exbin.auxiliary.paged_data.BinaryData;
import org.exbin.auxiliary.paged_data.ByteArrayEditableData;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Content binary data.
 */
@ParametersAreNonnullByDefault
public class ContentBinaryData implements BinaryData {

    public static final int PAGE_SIZE = 2048;

    private final Content dataSource;

    private final CachePage[] pages = new CachePage[2];
    private int nextPage = 0;

    public ContentBinaryData(Content dataSource) {
        this.dataSource = dataSource;
        pages[0] = new CachePage();
        pages[1] = new CachePage();
    }

    @Override
    public boolean isEmpty() {
        return dataSource.getSize() == 0;
    }

    @Override
    public long getDataSize() {
        return dataSource.getSize();
    }

    @Override
    public byte getByte(long position) {
        long pageIndex = position / PAGE_SIZE;
        int pageOffset = (int) (position % PAGE_SIZE);
        CachePage page;

        if (pages[0].index == pageIndex && pages[0].data != null) {
            page = pages[0];
        } else if (pages[1].index == pageIndex && pages[1].data != null) {
            page = pages[1];
        } else {
            byte[] data = getPage(pageIndex);
            if (data == null) {
                return -1;
            }

            pages[nextPage].data = data;
            pages[nextPage].index = pageIndex;
            page = pages[nextPage];
            nextPage = 1 - nextPage;
        }

        if (pageOffset >= page.data.length) {
            return -1;
        }

        return page.data[pageOffset];
    }

    @Nonnull
    @Override
    public BinaryData copy() {
        return copy(0, getDataSize());
    }

    @Nonnull
    @Override
    public BinaryData copy(long startFrom, long length) {
        ByteArrayEditableData result = new ByteArrayEditableData();
        result.insertUninitialized(0, length);
        int offset = 0;

        while (length > 0) {
            long pageIndex = startFrom / PAGE_SIZE;
            int pageOffset = (int) (startFrom % PAGE_SIZE);
            CachePage page;

            if (pages[0].index == pageIndex && pages[0].data != null) {
                page = pages[0];
            } else if (pages[1].index == pageIndex && pages[1].data != null) {
                page = pages[1];
            } else {
                byte[] data = getPage(pageIndex);
                if (data == null) {
                    throw createIndexOutOfBoundsException();
                }

                pages[nextPage].data = data;
                pages[nextPage].index = pageIndex;
                page = pages[nextPage];
                nextPage = 1 - nextPage;
            }

            if (pageOffset >= page.data.length) {
                throw createIndexOutOfBoundsException();
            }

            int copyLength = length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) length;
            if (pageOffset + copyLength > page.data.length) {
                copyLength = page.data.length - pageOffset;
            }
            if (copyLength == 0) {
                throw createIndexOutOfBoundsException();
            }

            result.replace(offset, page.data, pageOffset, copyLength);
            startFrom += copyLength;
            offset += copyLength;
            length -= copyLength;
        }

        return result;
    }

    @Override
    public void copyToArray(long startFrom, byte[] target, int offset, int length) {
        while (length > 0) {
            long pageIndex = startFrom / PAGE_SIZE;
            int pageOffset = (int) (startFrom % PAGE_SIZE);
            CachePage page;

            if (pages[0].index == pageIndex && pages[0].data != null) {
                page = pages[0];
            } else if (pages[1].index == pageIndex && pages[1].data != null) {
                page = pages[1];
            } else {
                byte[] data = getPage(pageIndex);
                if (data == null) {
                    throw createIndexOutOfBoundsException();
                }

                pages[nextPage].data = data;
                pages[nextPage].index = pageIndex;
                page = pages[nextPage];
                nextPage = 1 - nextPage;
            }

            if (pageOffset >= page.data.length) {
                throw createIndexOutOfBoundsException();
            }

            int copyLength = length;
            if (pageOffset + copyLength > page.data.length) {
                copyLength = page.data.length - pageOffset;
            }
            if (copyLength == 0) {
                throw createIndexOutOfBoundsException();
            }

            System.arraycopy(page.data, pageOffset, target, offset, copyLength);
            startFrom += copyLength;
            offset += copyLength;
            length -= copyLength;
        }
    }

    private byte[] getPage(long pageIndex) {
        long position = pageIndex * PAGE_SIZE;
        long size = dataSource.getSize();
        if (position > size) {
            return new byte[0];
        }
        int pageSize = position + PAGE_SIZE > size ? (int) (size - position) : PAGE_SIZE;
        byte[] bytes = new byte[pageSize];
        try {
            dataSource.read(bytes, position, pageSize);
        } catch (TskCoreException ex) {
            throw new TskReadException("Error when trying to read data", ex, position, pageSize);
        }
        return bytes;
    }

    @Override
    public void saveToStream(OutputStream outputStream) throws IOException {
        throw new UnsupportedOperationException("Save to stream is not supported");
    }

    @Nonnull
    @Override
    public InputStream getDataInputStream() {
        throw new UnsupportedOperationException("Data input stream is not supported");
    }

    @Override
    public void dispose() {
    }

    @Nonnull
    private static IndexOutOfBoundsException createIndexOutOfBoundsException() {
        return new IndexOutOfBoundsException("Requested data out of bounds");
    }

    public void clearCache() {
        pages[0].clear();
        pages[1].clear();
    }

    private static class CachePage {

        long index = 0;
        byte[] data = null;

        void clear() {
            index = -1;
            data = null;
        }
    }

    public static class TskReadException extends RuntimeException {

        private final long position;
        private final int length;

        public TskReadException(String string, Throwable thrwbl, long position, int length) {
            super(string, thrwbl);
            this.position = position;
            this.length = length;
        }

        public long getPosition() {
            return position;
        }

        public int getLength() {
            return length;
        }
    }
}
