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
 * Column types of an ESE database (JET_coltyp).
 */
public enum EseColumnType {
    NIL(0, "Nil", 0),
    BIT(1, "Bit", 1),
    UNSIGNED_BYTE(2, "UnsignedByte", 1),
    SHORT(3, "Short", 2),
    LONG(4, "Long", 4),
    CURRENCY(5, "Currency", 8),
    IEEE_SINGLE(6, "IEEESingle", 4),
    IEEE_DOUBLE(7, "IEEEDouble", 8),
    DATE_TIME(8, "DateTime", 8),
    BINARY(9, "Binary", 0),
    TEXT(10, "Text", 0),
    LONG_BINARY(11, "LongBinary", 0),
    LONG_TEXT(12, "LongText", 0),
    SLV(13, "SLV", 0),
    UNSIGNED_LONG(14, "UnsignedLong", 4),
    LONG_LONG(15, "LongLong", 8),
    GUID(16, "GUID", 16),
    UNSIGNED_SHORT(17, "UnsignedShort", 2),
    UNKNOWN(-1, "Unknown", 0);

    private final int identifier;
    private final String displayName;
    private final int fixedSize;

    EseColumnType(int identifier, String displayName, int fixedSize) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.fixedSize = fixedSize;
    }

    /**
     * @return The JET_coltyp value.
     */
    public int getIdentifier() {
        return identifier;
    }

    /**
     * @return The name used by the ESE API, e.g. "LongText".
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return The size in bytes of a value of this type when it is a fixed size
     *         type, 0 otherwise.
     */
    public int getFixedSize() {
        return fixedSize;
    }

    /**
     * @return True for Text and LongText columns.
     */
    public boolean isText() {
        return this == TEXT || this == LONG_TEXT;
    }

    /**
     * @return True for Binary and LongBinary columns.
     */
    public boolean isBinary() {
        return this == BINARY || this == LONG_BINARY;
    }

    static EseColumnType fromIdentifier(int identifier) {
        for (EseColumnType type : values()) {
            if (type.identifier == identifier) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
