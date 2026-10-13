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

/**
 * A column of an ESE table.
 */
public final class EseColumn {

    /**
     * Code page of Unicode (UTF-16LE) text columns.
     */
    public static final int CODE_PAGE_UNICODE = 1200;
    /**
     * Code page of Western (Windows-1252) text columns.
     */
    public static final int CODE_PAGE_WESTERN = 1252;
    /**
     * Code page of US-ASCII text columns.
     */
    public static final int CODE_PAGE_ASCII = 20127;

    static final int FIRST_VARIABLE_IDENTIFIER = 128;
    static final int FIRST_TAGGED_IDENTIFIER = 256;

    private final int identifier;
    private final String name;
    private final EseColumnType type;
    private final int size;
    private final int codePage;
    private final int flags;
    private final byte[] defaultValue;
    private final boolean derived;
    /**
     * Offset of a fixed column's value from the start of the fixed data; set
     * by the table once all columns are known.
     */
    int fixedOffset;

    EseColumn(int identifier, String name, EseColumnType type, int size, int codePage, int flags, byte[] defaultValue, boolean derived) {
        this.identifier = identifier;
        this.name = name;
        this.type = type;
        this.size = size > 0 ? size : type.getFixedSize();
        this.codePage = codePage;
        this.flags = flags;
        this.defaultValue = defaultValue;
        this.derived = derived;
    }

    /**
     * Creates a copy of this column marked as inherited from a template table.
     */
    EseColumn asDerived() {
        return new EseColumn(identifier, name, type, size, codePage, flags, defaultValue, true);
    }

    /**
     * @return The column identifier (FID). Fixed columns have identifiers
     *         below 128, variable columns 128 to 255 and tagged columns 256
     *         and above.
     */
    public int getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public EseColumnType getType() {
        return type;
    }

    /**
     * @return The size in bytes of a fixed column's values; the maximum size
     *         for the other kinds, 0 if unlimited.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The code page of a text column (1200 = UTF-16LE, 1252 =
     *         Windows-1252, 20127 = ASCII).
     */
    public int getCodePage() {
        return codePage;
    }

    /**
     * @return The JET_bitColumn* flags of the column.
     */
    public int getFlags() {
        return flags;
    }

    public boolean isFixed() {
        return identifier < FIRST_VARIABLE_IDENTIFIER;
    }

    public boolean isVariable() {
        return identifier >= FIRST_VARIABLE_IDENTIFIER && identifier < FIRST_TAGGED_IDENTIFIER;
    }

    public boolean isTagged() {
        return identifier >= FIRST_TAGGED_IDENTIFIER;
    }

    /**
     * @return True if the column was inherited from the table's template
     *         table.
     */
    public boolean isDerived() {
        return derived;
    }

    /**
     * @return The raw default value used for records that do not store this
     *         column, or null if the column has no default.
     */
    byte[] getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String toString() {
        return String.format("EseColumn[%d %s %s]", identifier, name, type.getDisplayName());
    }
}
