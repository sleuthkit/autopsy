/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils.esedb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * A logical page of an ESE database: a page header followed by node data,
 * with an array of tags growing backwards from the end of the page that
 * locates each node. Pages form B+trees; branch nodes point at child pages
 * and leaf nodes carry the data (records, long value chunks or index
 * entries).
 */
final class Page {

    static final int FLAG_ROOT = 0x0001;
    static final int FLAG_LEAF = 0x0002;
    static final int FLAG_PARENT_OF_LEAF = 0x0004;
    static final int FLAG_EMPTY = 0x0008;
    static final int FLAG_SPACE_TREE = 0x0020;
    static final int FLAG_INDEX = 0x0040;
    static final int FLAG_LONG_VALUE = 0x0080;
    static final int FLAG_NEW_RECORD_FORMAT = 0x0800;

    private static final int TAG_FLAG_DELETED = 0x02;
    private static final int TAG_FLAG_COMPRESSED = 0x04;

    private static final int SMALL_PAGE_HEADER_SIZE = 40;
    private static final int LARGE_PAGE_HEADER_SIZE = 80;

    private final int number;
    private final ByteBuffer buffer;
    private final boolean smallPage;
    private final int dataStart;
    private final int dataEnd;
    private final int previousPage;
    private final int nextPage;
    private final int flags;
    private final int tagCount;
    private final int nodeCount;
    private byte[] keyPrefix;

    /**
     * @param number    Logical page number.
     * @param data      The raw page, exactly one page size long.
     * @param smallPage True if the database uses pages of at most 8 KiB.
     */
    Page(int number, byte[] data, boolean smallPage) throws EseException {
        this.number = number;
        this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.smallPage = smallPage;
        int headerSize = smallPage ? SMALL_PAGE_HEADER_SIZE : LARGE_PAGE_HEADER_SIZE;
        if (data.length < headerSize) {
            throw new EseException(String.format("Page %d is shorter than its header", number));
        }
        this.previousPage = buffer.getInt(16);
        this.nextPage = buffer.getInt(20);
        int freeDataOffset = buffer.getShort(32) & 0xFFFF;
        int tagState = buffer.getShort(34) & 0xFFFF;
        this.flags = buffer.getInt(36);
        this.dataStart = headerSize;
        this.dataEnd = Math.min(data.length, headerSize + freeDataOffset);
        // On newer versions the upper 4 bits hold a reserved tag count.
        int reservedTags = tagState >> 12;
        this.tagCount = tagState & 0x0FFF;
        this.nodeCount = Math.max(0, tagCount - (reservedTags == 0 ? 1 : reservedTags));
    }

    int getNumber() {
        return number;
    }

    int getPreviousPage() {
        return previousPage;
    }

    int getNextPage() {
        return nextPage;
    }

    int getFlags() {
        return flags;
    }

    boolean isRoot() {
        return (flags & FLAG_ROOT) != 0;
    }

    boolean isLeaf() {
        return (flags & FLAG_LEAF) != 0;
    }

    boolean isBranch() {
        return !isLeaf();
    }

    boolean isNewRecordFormat() {
        return (flags & FLAG_NEW_RECORD_FORMAT) != 0;
    }

    int getNodeCount() {
        return nodeCount;
    }

    /**
     * Key prefix shared by the nodes of this page (empty on root pages, whose
     * first tag holds the root header instead).
     */
    private byte[] getKeyPrefix() throws EseException {
        if (keyPrefix == null) {
            keyPrefix = isRoot() ? new byte[0] : getTag(0).getData();
        }
        return keyPrefix;
    }

    /**
     * @param index Tag index; tag 0 is the page's own entry, nodes start at 1.
     */
    private Tag getTag(int index) throws EseException {
        if (index < 0 || index >= tagCount) {
            throw new EseException(String.format("Tag %d does not exist on page %d (%d tags)", index, number, tagCount));
        }
        int tagOffset = buffer.capacity() - (index + 1) * 4;
        if (tagOffset < dataStart) {
            throw new EseException(String.format("Tag %d of page %d overlaps the page header", index, number));
        }
        int rawSize = buffer.getShort(tagOffset) & 0xFFFF;
        int rawOffset = buffer.getShort(tagOffset + 2) & 0xFFFF;
        int mask = smallPage ? 0x1FFF : 0x7FFF;
        int size = rawSize & mask;
        int offset = rawOffset & mask;
        if (size == 0 && index != 0) {
            throw new EseException(String.format("Tag %d of page %d has no data (corrupt page?)", index, number));
        }
        int start = dataStart + offset;
        if (start + size > buffer.capacity() || start > dataEnd && size > 0) {
            throw new EseException(String.format("Tag %d of page %d points outside the page", index, number));
        }
        // Copy with absolute offsets: pages are cached and may be read by several threads at once,
        // so the shared buffer's position must never be touched.
        byte[] data = new byte[size];
        System.arraycopy(buffer.array(), start, data, 0, size);
        int tagFlags;
        if (smallPage) {
            // Small pages keep the flags in the 3 most significant bits of the offset.
            tagFlags = rawOffset >> 13;
        } else if (size >= 2) {
            // Large pages keep them in the 3 most significant bits of the first word of the data.
            tagFlags = (data[1] & 0xFF) >> 5;
        } else {
            tagFlags = 0;
        }
        return new Tag(data, tagFlags);
    }

    /**
     * @param index Node index, 0 based.
     */
    Node getNode(int index) throws EseException {
        if (index < 0 || index >= nodeCount) {
            throw new EseException(String.format("Node %d does not exist on page %d (%d nodes)", index, number, nodeCount));
        }
        Tag tag = getTag(index + 1);
        byte[] data = tag.getData();
        int offset = 0;
        byte[] prefix = new byte[0];
        if (tag.isCompressed() && data.length >= 2) {
            int prefixSize = readShort(data, 0) & 0x1FFF;
            byte[] pagePrefix = getKeyPrefix();
            prefix = new byte[prefixSize];
            System.arraycopy(pagePrefix, 0, prefix, 0, Math.min(prefixSize, pagePrefix.length));
            offset += 2;
        }
        byte[] suffix = new byte[0];
        if (data.length >= offset + 2) {
            int suffixSize = readShort(data, offset) & 0x1FFF;
            offset += 2;
            suffixSize = Math.min(suffixSize, data.length - offset);
            suffix = new byte[suffixSize];
            System.arraycopy(data, offset, suffix, 0, suffixSize);
            offset += suffixSize;
        }
        byte[] key = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, key, 0, prefix.length);
        System.arraycopy(suffix, 0, key, prefix.length, suffix.length);
        byte[] nodeData = new byte[data.length - offset];
        System.arraycopy(data, offset, nodeData, 0, nodeData.length);
        return new Node(this, index, key, nodeData, tag.isDeleted());
    }

    List<Node> getNodes() throws EseException {
        List<Node> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(getNode(i));
        }
        return nodes;
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    /**
     * The physical entry of a page: its data and flags.
     */
    private static final class Tag {

        private final byte[] data;
        private final int flags;

        Tag(byte[] data, int flags) {
            this.data = data;
            this.flags = flags;
        }

        byte[] getData() {
            return data;
        }

        boolean isCompressed() {
            return (flags & TAG_FLAG_COMPRESSED) != 0;
        }

        boolean isDeleted() {
            return (flags & TAG_FLAG_DELETED) != 0;
        }
    }

    /**
     * The logical entry of a page: a key and either the number of a child
     * page (branch pages) or data (leaf pages).
     */
    static final class Node {

        private final Page page;
        private final int index;
        private final byte[] key;
        private final byte[] data;
        private final boolean deleted;

        Node(Page page, int index, byte[] key, byte[] data, boolean deleted) {
            this.page = page;
            this.index = index;
            this.key = key;
            this.data = data;
            this.deleted = deleted;
        }

        Page getPage() {
            return page;
        }

        int getIndex() {
            return index;
        }

        byte[] getKey() {
            return key;
        }

        byte[] getData() {
            return data;
        }

        boolean isDeleted() {
            return deleted;
        }

        /**
         * @return The child page number of a branch node.
         */
        int getChildPage() throws EseException {
            if (data.length < 4) {
                throw new EseException(String.format("Branch node %d of page %d has no child page number", index, page.number));
            }
            return (data[0] & 0xFF) | ((data[1] & 0xFF) << 8) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 24);
        }
    }

    /**
     * Compares two keys the way ESE orders them: byte by byte as unsigned
     * values, a shorter key sorting before a longer key it is a prefix of.
     */
    static int compareKeys(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diff != 0) {
                return diff;
            }
        }
        return a.length - b.length;
    }
}
