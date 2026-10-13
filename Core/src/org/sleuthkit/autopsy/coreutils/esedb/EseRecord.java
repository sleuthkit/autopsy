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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A record (row) of an ESE table. Values are decoded lazily, column by
 * column, from the node data of the record.
 *
 * Decoded value types: Boolean (Bit), Integer (UnsignedByte, Short, Long,
 * UnsignedShort), Long (Currency, UnsignedLong, LongLong and the raw 8 bytes
 * of DateTime, see EseDateTime), Float, Double, String (Text, LongText,
 * GUID), byte[] (Binary, LongBinary, unknown types) and List of those for
 * multi-valued columns.
 */
public final class EseRecord {

    private static final int RECORD_HEADER_SIZE = 4;

    private static final int TAGFLD_OFFSET_MASK_SMALL = 0x1FFF;
    private static final int TAGFLD_OFFSET_MASK_LARGE = 0x7FFF;
    private static final int TAGFLD_NULL_SMALL_PAGE = 0x2000;
    private static final int TAGFLD_EXTENDED_INFO = 0x4000;
    private static final int TAGFLD_DERIVED = 0x8000;

    private static final int TAGFLD_HEADER_COMPRESSED = 0x02;
    private static final int TAGFLD_HEADER_SEPARATED = 0x04;
    private static final int TAGFLD_HEADER_MULTI_VALUES = 0x08;
    private static final int TAGFLD_HEADER_TWO_VALUES = 0x10;
    private static final int TAGFLD_HEADER_NULL = 0x20;

    private static final int MULTI_VALUE_SEPARATED_INSTANCE = 0x8000;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final EseTable table;
    private final byte[] data;
    private final boolean smallPage;
    private final boolean newRecordFormat;

    private final int lastFixedId;
    private final int lastVariableId;
    private final int variableOffsetStart;
    private final int fixedNullBitmapStart;
    private final int variableDataStart;
    private final int[] variableOffsets;
    private final int taggedDataStart;
    private final int taggedFieldCount;

    EseRecord(EseTable table, byte[] data, boolean smallPage, boolean newRecordFormat) {
        this.table = table;
        this.data = data;
        this.smallPage = smallPage;
        this.newRecordFormat = newRecordFormat;
        if (data.length >= RECORD_HEADER_SIZE) {
            lastFixedId = data[0] & 0xFF;
            lastVariableId = data[1] & 0xFF;
            variableOffsetStart = readShort(data, 2);
            fixedNullBitmapStart = variableOffsetStart - ((lastFixedId + 7) / 8);
            int variableCount = Math.max(0, lastVariableId - (EseColumn.FIRST_VARIABLE_IDENTIFIER - 1));
            variableDataStart = variableOffsetStart + variableCount * 2;
            if (variableCount > 0 && data.length >= variableDataStart) {
                variableOffsets = new int[variableCount];
                for (int i = 0; i < variableCount; i++) {
                    variableOffsets[i] = readShort(data, variableOffsetStart + i * 2);
                }
            } else {
                variableOffsets = new int[0];
            }
            int tagged = variableDataStart;
            if (variableOffsets.length > 0) {
                tagged += variableOffsets[variableOffsets.length - 1] & 0x7FFF;
            }
            taggedDataStart = tagged;
            if (data.length >= taggedDataStart + 4) {
                int firstOffset = tagFieldOffset(readShort(data, taggedDataStart + 2));
                taggedFieldCount = firstOffset / 4;
            } else {
                taggedFieldCount = 0;
            }
        } else {
            lastFixedId = 0;
            lastVariableId = 0;
            variableOffsetStart = 0;
            fixedNullBitmapStart = 0;
            variableDataStart = 0;
            variableOffsets = new int[0];
            taggedDataStart = 0;
            taggedFieldCount = 0;
        }
    }

    /**
     * @return The table this record belongs to.
     */
    public EseTable getTable() {
        return table;
    }

    /**
     * Decodes the value of a column.
     *
     * @return The decoded value (see the class comment), or null if the
     *         column is null in this record.
     *
     * @throws EseException If the value is corrupt or compressed with an
     *                      unsupported scheme.
     */
    public Object getValue(EseColumn column) throws EseException {
        if (data.length < RECORD_HEADER_SIZE) {
            return null;
        }
        if (column.isFixed()) {
            byte[] raw = getFixed(column);
            return raw == null ? null : decode(column, raw);
        }
        if (column.isVariable()) {
            byte[] raw = getVariable(column);
            return raw == null ? null : decode(column, raw);
        }
        return getTagged(column);
    }

    /**
     * @return The values of every column of the table, by column name, in
     *         column order. A value that cannot be decoded is replaced by an
     *         EseException.
     */
    public Map<String, Object> getValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (EseColumn column : table.getColumns()) {
            try {
                values.put(column.getName(), getValue(column));
            } catch (EseException | RuntimeException ex) {
                values.put(column.getName(), new EseException(String.format("Column %s could not be decoded: %s", column.getName(), ex.getMessage()), ex));
            }
        }
        return values;
    }

    private byte[] getFixed(EseColumn column) throws EseException {
        int id = column.getIdentifier();
        if (id > lastFixedId) {
            return column.getDefaultValue();
        }
        int bit = id - 1;
        int bitmapByte = fixedNullBitmapStart + bit / 8;
        if (bitmapByte < 0 || bitmapByte >= data.length) {
            throw new EseException("Fixed column null bitmap lies outside the record");
        }
        if ((data[bitmapByte] & (1 << (bit % 8))) != 0) {
            return null;
        }
        int offset = RECORD_HEADER_SIZE + column.fixedOffset;
        return slice(offset, offset + column.getSize());
    }

    private byte[] getVariable(EseColumn column) throws EseException {
        int id = column.getIdentifier();
        if (id > lastVariableId) {
            return column.getDefaultValue();
        }
        int index = id - EseColumn.FIRST_VARIABLE_IDENTIFIER;
        if (index >= variableOffsets.length) {
            throw new EseException("Variable column offset array is truncated");
        }
        int end = variableOffsets[index];
        if ((end & 0x8000) != 0) {
            return null;
        }
        int start = index == 0 ? 0 : variableOffsets[index - 1] & 0x7FFF;
        return slice(variableDataStart + start, variableDataStart + end);
    }

    private Object getTagged(EseColumn column) throws EseException {
        int index = findTagField(column.getIdentifier(), column.isDerived());
        if (index < 0) {
            byte[] defaultValue = column.getDefaultValue();
            return defaultValue == null ? null : decode(column, defaultValue);
        }
        if (!newRecordFormat) {
            throw new EseException("Tagged columns in the old record format are not supported");
        }
        int fieldOffsetRaw = readShort(data, taggedDataStart + index * 4 + 2);
        int fieldOffset = tagFieldOffset(fieldOffsetRaw);
        boolean extendedInfo = smallPage ? (fieldOffsetRaw & TAGFLD_EXTENDED_INFO) != 0 : true;
        int flags = 0;
        if (extendedInfo) {
            int flagsOffset = taggedDataStart + fieldOffset;
            if (flagsOffset >= data.length) {
                throw new EseException("Tagged column header lies outside the record");
            }
            flags = data[flagsOffset] & 0xFF;
        }
        boolean isNull = smallPage ? (fieldOffsetRaw & TAGFLD_NULL_SMALL_PAGE) != 0 : (flags & TAGFLD_HEADER_NULL) != 0;
        if (isNull) {
            return null;
        }
        int dataStart = fieldOffset + (extendedInfo ? 1 : 0);
        int dataEnd = index + 1 < taggedFieldCount
                ? tagFieldOffset(readShort(data, taggedDataStart + (index + 1) * 4 + 2))
                : data.length - taggedDataStart;
        byte[] raw = slice(taggedDataStart + dataStart, taggedDataStart + dataEnd);

        if ((flags & TAGFLD_HEADER_MULTI_VALUES) != 0) {
            List<byte[]> instances = multiValues(raw, flags);
            List<Object> values = new ArrayList<>(instances.size());
            for (byte[] instance : instances) {
                values.add(decode(column, instance));
            }
            return values;
        }
        if ((flags & TAGFLD_HEADER_SEPARATED) != 0) {
            raw = table.getLongValue(raw);
        } else if ((flags & TAGFLD_HEADER_COMPRESSED) != 0) {
            raw = EseCompression.decompress(raw);
        }
        return decode(column, raw);
    }

    /**
     * Splits the instances of a multi-valued tagged column.
     */
    private List<byte[]> multiValues(byte[] raw, int flags) throws EseException {
        List<byte[]> values = new ArrayList<>();
        if ((flags & TAGFLD_HEADER_TWO_VALUES) != 0) {
            // Compact storage of exactly two values: the first byte is the size of the first value.
            if (raw.length < 1) {
                throw new EseException("Two-value column is empty");
            }
            int firstSize = raw[0] & 0xFF;
            if (1 + firstSize > raw.length) {
                throw new EseException("Two-value column is truncated");
            }
            values.add(slice(raw, 1, 1 + firstSize));
            values.add(slice(raw, 1 + firstSize, raw.length));
        } else {
            // An array of 16 bit offsets, then the values. Bit 15 of an offset marks an instance stored as a long value.
            if (raw.length < 2) {
                throw new EseException("Multi-value column is empty");
            }
            int firstOffset = readShort(raw, 0) & 0x7FFF;
            int count = firstOffset / 2;
            if (firstOffset > raw.length) {
                throw new EseException("Multi-value column offsets are truncated");
            }
            for (int i = 0; i < count; i++) {
                int offset = readShort(raw, i * 2);
                int end = i + 1 < count ? readShort(raw, (i + 1) * 2) & 0x7FFF : raw.length;
                byte[] instance = slice(raw, offset & 0x7FFF, end);
                if ((offset & MULTI_VALUE_SEPARATED_INSTANCE) != 0) {
                    instance = table.getLongValue(instance);
                }
                values.add(instance);
            }
        }
        if ((flags & TAGFLD_HEADER_COMPRESSED) != 0 && !values.isEmpty()) {
            // Only the first instance is compressed.
            values.set(0, EseCompression.decompress(values.get(0)));
        }
        return values;
    }

    /**
     * Binary search of the TAGFLD array for a column, ordered the way
     * TAGFLD::CmpTagfld2 orders it (derived columns first).
     *
     * @return Index in the TAGFLD array, or -1.
     */
    private int findTagField(int identifier, boolean derived) {
        if (taggedFieldCount == 0) {
            return -1;
        }
        int lookup = identifier | (derived ? TAGFLD_DERIVED << 16 : 0);
        int flip = TAGFLD_DERIVED << 16;
        int mask = flip | 0xFFFF;
        int wanted = flip ^ (lookup & mask);
        int min = 0;
        int max = taggedFieldCount - 1;
        while (min != max) {
            int test = min + (max - min) / 2;
            int tagValue = readInt(data, taggedDataStart + test * 4);
            int candidate = flip ^ (tagValue & mask);
            if (Integer.compareUnsigned(candidate, wanted) < 0) {
                min = test + 1;
            } else if (candidate == wanted) {
                min = test;
                break;
            } else {
                max = test;
            }
        }
        int tagValue = readInt(data, taggedDataStart + min * 4);
        return (tagValue & 0xFFFF) == identifier ? min : -1;
    }

    private int tagFieldOffset(int rawOffset) {
        return rawOffset & (smallPage ? TAGFLD_OFFSET_MASK_SMALL : TAGFLD_OFFSET_MASK_LARGE);
    }

    /**
     * Converts raw column bytes to a Java value according to the column type.
     */
    static Object decode(EseColumn column, byte[] raw) throws EseException {
        EseColumnType type = column.getType();
        try {
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            switch (type) {
                case BIT:
                    return raw.length > 0 && (raw[0] & 0xFF) == 0xFF;
                case UNSIGNED_BYTE:
                    return raw.length > 0 ? raw[0] & 0xFF : null;
                case SHORT:
                    return (int) buffer.getShort();
                case UNSIGNED_SHORT:
                    return buffer.getShort() & 0xFFFF;
                case LONG:
                    return buffer.getInt();
                case UNSIGNED_LONG:
                    return buffer.getInt() & 0xFFFFFFFFL;
                case CURRENCY:
                case LONG_LONG:
                case DATE_TIME:
                    return buffer.getLong();
                case IEEE_SINGLE:
                    return buffer.getFloat();
                case IEEE_DOUBLE:
                    return buffer.getDouble();
                case GUID:
                    return guid(buffer);
                case TEXT:
                case LONG_TEXT:
                    return text(raw, column.getCodePage());
                default:
                    return raw;
            }
        } catch (java.nio.BufferUnderflowException ex) {
            throw new EseException(String.format("Value of %s column %s is too short (%d bytes)", type.getDisplayName(), column.getName(), raw.length), ex);
        }
    }

    private static String guid(ByteBuffer buffer) {
        int data1 = buffer.getInt();
        int data2 = buffer.getShort() & 0xFFFF;
        int data3 = buffer.getShort() & 0xFFFF;
        long data4 = buffer.order(ByteOrder.BIG_ENDIAN).getLong();
        long high = ((long) data1 << 32) | ((long) data2 << 16) | data3;
        return new UUID(high, data4).toString();
    }

    private static String text(byte[] raw, int codePage) {
        String text;
        if (codePage == EseColumn.CODE_PAGE_UNICODE) {
            byte[] bytes = raw;
            if (bytes.length % 2 != 0) {
                bytes = new byte[raw.length + 1];
                System.arraycopy(raw, 0, bytes, 0, raw.length);
            }
            text = new String(bytes, StandardCharsets.UTF_16LE);
        } else if (codePage == EseColumn.CODE_PAGE_ASCII) {
            text = new String(raw, StandardCharsets.US_ASCII);
        } else {
            text = new String(raw, WINDOWS_1252);
        }
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\0') {
            end--;
        }
        return text.substring(0, end);
    }

    private byte[] slice(int start, int end) throws EseException {
        return slice(data, start, end);
    }

    private static byte[] slice(byte[] source, int start, int end) throws EseException {
        if (start < 0 || end > source.length || start > end) {
            throw new EseException(String.format("Value range %d-%d lies outside the record (%d bytes)", start, end, source.length));
        }
        byte[] result = new byte[end - start];
        System.arraycopy(source, start, result, 0, result.length);
        return result;
    }

    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8) | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    /**
     * @return An unmodifiable view of the columns the record can be asked
     *         for; convenience for callers that only hold a record.
     */
    public List<EseColumn> getColumns() {
        return Collections.unmodifiableList(table.getColumns());
    }
}
