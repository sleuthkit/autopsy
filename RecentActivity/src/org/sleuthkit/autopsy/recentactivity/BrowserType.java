 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author arivera
 */
public enum BrowserType {

    IE(0), //Internet Explorer
    FF(1), //Firefox
    CH(2); //Chrome
    private static final Map<Integer, BrowserType> lookup = new HashMap<Integer, BrowserType>();

    static {
        for (BrowserType bt : values()) {
            lookup.put(bt.type, bt);
        }
    }
    private int type;

    private BrowserType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static BrowserType get(int type) {
        switch (type) {
            case 0:
                return IE;
            case 1:
                return FF;
            case 2:
                return CH;
        }
        return null;
    }
}
