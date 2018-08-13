/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileselector;

import org.apache.commons.validator.routines.EmailValidator;

/**
 * Determines the type of a database cell (EMAIL, PHONE, GPS COORD, MAC ADDRESS)
 */
final class CellTypeDetector {
    
    private static final EmailValidator EMAIL_VALIDATOR;
    
    static {
        EMAIL_VALIDATOR = EmailValidator.getInstance();
    }
    
    public static CellType getType(Object cell) {
        if (cell instanceof String) {
            return getType((String) cell);
        }
        return CellType.NOT_INTERESTING;
    }
    
    public static CellType getType(String cell) {
        if(EMAIL_VALIDATOR.isValid(cell)) {
            return CellType.EMAIL;
        }
        return CellType.NOT_INTERESTING;
    } 
    
    /*
     * Cell data that qualify as an "interesting" selector
     */
    public enum CellType {
        EMAIL,
        NOT_INTERESTING
    }
}