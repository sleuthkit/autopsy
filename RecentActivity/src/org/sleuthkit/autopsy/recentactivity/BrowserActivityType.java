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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author arivera
 */
public enum BrowserActivityType {

    Cookies(0),
    Url(1),
    Bookmarks(2);
    private static final Map<Integer, BrowserActivityType> lookup = new HashMap<Integer, BrowserActivityType>();

    static {
        for (BrowserActivityType bat : values()) {
            lookup.put(bat.type, bat);
        }
    }
    private int type;

    private BrowserActivityType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public static BrowserActivityType get(int type) {
        switch (type) {
            case 0:
                return Cookies;
            case 1:
                return Url;
            case 2:
                return Bookmarks;
        }
        return null;
    }
}
