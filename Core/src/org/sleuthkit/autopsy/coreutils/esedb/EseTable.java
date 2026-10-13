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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A table of an ESE database: its columns and a way to iterate over its
 * records. Instances are created by EseDatabase from the catalog.
 */
public final class EseTable {

    private final EseDatabase database;
    private final int objectId;
    private final String name;
    private final int rootPage;
    private final String templateName;
    private final List<EseColumn> columns = new ArrayList<>();
    private final Map<String, EseColumn> columnsByName = new LinkedHashMap<>();
    private int longValueRootPage;

    EseTable(EseDatabase database, int objectId, String name, int rootPage, String templateName) {
        this.database = database;
        this.objectId = objectId;
        this.name = name;
        this.rootPage = rootPage;
        this.templateName = templateName;
    }

    /**
     * @return The object identifier of the table in the catalog.
     */
    public int getObjectId() {
        return objectId;
    }

    public String getName() {
        return name;
    }

    /**
     * @return The logical page number of the root of the table's data tree.
     */
    public int getRootPage() {
        return rootPage;
    }

    /**
     * @return The name of the template table this table derives from, if
     *         any.
     */
    public Optional<String> getTemplateName() {
        return Optional.ofNullable(templateName);
    }

    /**
     * @return True for the engine's own tables (MSysObjects, MSysLocales,
     *         ...).
     */
    public boolean isSystemTable() {
        return name.startsWith("MSys"); //NON-NLS
    }

    /**
     * @return The columns of the table, ordered by column identifier
     *         (fixed, then variable, then tagged columns).
     */
    public List<EseColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public Optional<EseColumn> getColumn(String columnName) {
        return Optional.ofNullable(columnsByName.get(columnName));
    }

    void addColumn(EseColumn column) {
        if (columnsByName.containsKey(column.getName())) {
            return;
        }
        columns.add(column);
        columnsByName.put(column.getName(), column);
    }

    boolean hasLongValueTree() {
        return longValueRootPage != 0;
    }

    void setLongValueRootPage(int page) {
        this.longValueRootPage = page;
    }

    int longValueRootPageForDerived() {
        return longValueRootPage;
    }

    /**
     * Sorts the columns by identifier and computes the offsets of the fixed
     * columns; called once the catalog has been read.
     */
    void finishColumns() {
        columns.sort(Comparator.comparingInt(EseColumn::getIdentifier));
        int offset = 0;
        for (EseColumn column : columns) {
            if (column.isFixed()) {
                column.fixedOffset = offset;
                offset += column.getSize();
            }
        }
    }

    /**
     * Iterates over every record of the table in primary key order. The
     * iterator reads pages as it goes; errors in the page structure surface
     * as an EseException wrapped in an UncheckedEseException.
     */
    public Iterator<EseRecord> records() throws EseException {
        final Iterator<Page.Node> nodes = database.leafNodes(rootPage);
        return new Iterator<EseRecord>() {
            @Override
            public boolean hasNext() {
                return nodes.hasNext();
            }

            @Override
            public EseRecord next() {
                if (!nodes.hasNext()) {
                    throw new NoSuchElementException();
                }
                Page.Node node = nodes.next();
                return new EseRecord(EseTable.this, node.getData(), database.hasSmallPages(), node.getPage().isNewRecordFormat());
            }
        };
    }

    /**
     * Counts the records of the table (reads every data page).
     */
    public long countRecords() throws EseException {
        long count = 0;
        Iterator<Page.Node> nodes = database.leafNodes(rootPage);
        try {
            while (nodes.hasNext()) {
                nodes.next();
                count++;
            }
        } catch (UncheckedEseException ex) {
            throw ex.getCause();
        }
        return count;
    }

    /**
     * Reads a value stored in the table's long value tree.
     *
     * @param lid The long value identifier, as stored in the record (4 or 8
     *            bytes, little endian).
     */
    byte[] getLongValue(byte[] lid) throws EseException {
        if (longValueRootPage == 0) {
            throw new EseException(String.format("Table %s has no long value tree", name));
        }
        byte[] key = new byte[lid.length];
        for (int i = 0; i < lid.length; i++) {
            key[i] = lid[lid.length - 1 - i];
        }
        EseDatabase.Cursor cursor = database.search(longValueRootPage, key);
        Page.Node header = cursor.getNode();
        if (Page.compareKeys(header.getKey(), key) != 0) {
            throw new EseException(String.format("Long value %s not found in table %s", hex(key), name));
        }
        byte[] headerData = header.getData();
        if (headerData.length < 8) {
            throw new EseException(String.format("Long value %s of table %s has a truncated header", hex(key), name));
        }
        long size = ((headerData[4] & 0xFFL) | ((headerData[5] & 0xFFL) << 8) | ((headerData[6] & 0xFFL) << 16) | ((headerData[7] & 0xFFL) << 24));
        List<byte[]> chunks = new ArrayList<>();
        List<Long> chunkOffsets = new ArrayList<>();
        while (cursor.moveNext()) {
            Page.Node node = cursor.getNode();
            byte[] nodeKey = node.getKey();
            if (nodeKey.length < key.length + 4 || !startsWith(nodeKey, key)) {
                break;
            }
            chunks.add(node.getData());
            int n = nodeKey.length;
            long offset = ((nodeKey[n - 4] & 0xFFL) << 24) | ((nodeKey[n - 3] & 0xFFL) << 16) | ((nodeKey[n - 2] & 0xFFL) << 8) | (nodeKey[n - 1] & 0xFFL);
            chunkOffsets.add(offset);
        }
        chunkOffsets.add(size);
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE - 16, Math.max(16, size)));
        for (int i = 0; i < chunks.size(); i++) {
            byte[] chunk = chunks.get(i);
            // Each chunk's key carries its offset in the value; the next offset (or the total size) gives its expected size.
            long expected = chunkOffsets.get(i + 1) - chunkOffsets.get(i);
            if (chunk.length != expected) {
                // A chunk whose size does not match the offsets is compressed.
                chunk = EseCompression.decompress(chunk);
            }
            out.write(chunk, 0, chunk.length);
        }
        return out.toByteArray();
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("EseTable[%s]", name);
    }

    /**
     * Wraps an EseException thrown while an iterator reads pages.
     */
    public static final class UncheckedEseException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        UncheckedEseException(EseException cause) {
            super(cause.getMessage(), cause);
        }

        @Override
        public synchronized EseException getCause() {
            return (EseException) super.getCause();
        }
    }
}
