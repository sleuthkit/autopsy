/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

/**
 * Captures the count to be displayed in the UI.
 */
public class TreeCount {
    public enum Type {
        DETERMINATE,
        INDETERMINATE,
        NOT_SHOWN
    }
    
    private final Type type;
    private final long count;

    public static final TreeCount INDETERMINATE = new TreeCount(Type.INDETERMINATE, -1);
    public static final TreeCount NOT_SHOWN = new TreeCount(Type.NOT_SHOWN, -1);
    
    public static TreeCount getDeterminate(long count) {
        return new TreeCount(Type.DETERMINATE, count);
    }
    
    private TreeCount(Type type, long count) {
        this.type = type;
        this.count = count;
    }

    public Type getType() {
        return type;
    }

    public long getCount() {
        return count;
    }
}
