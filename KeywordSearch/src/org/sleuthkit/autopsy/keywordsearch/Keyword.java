/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.keywordsearch;

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Representation of single keyword to search for
 */
class Keyword {
    private String keywordString;   // keyword to search for
    private boolean isLiteral;  // false if reg exp
    private BlackboardAttribute.ATTRIBUTE_TYPE keywordType = null;

    /**
     * 
     * @param query Keyword to search for
     * @param isLiteral false if reg exp
     */
    Keyword(String query, boolean isLiteral) {
        this.keywordString = query;
        this.isLiteral = isLiteral;
    }
    
    /**
     * 
     * @param query Keyword to search for
     * @param isLiteral false if reg exp
     * @param keywordType 
     */
    Keyword(String query, boolean isLiteral, BlackboardAttribute.ATTRIBUTE_TYPE keywordType) {
        this(query, isLiteral);
        this.keywordType = keywordType;
    }
    
    void setType(BlackboardAttribute.ATTRIBUTE_TYPE keywordType) {
        this.keywordType = keywordType;
    }
    
    BlackboardAttribute.ATTRIBUTE_TYPE getType() {
        return this.keywordType;
    }

    /**
     * 
     * @return Keyword to search for
     */
    String getQuery() {
        return keywordString;
    }

    boolean isLiteral() {
        return isLiteral;
    }

    @Override
    public String toString() {
        return NbBundle.getMessage(this.getClass(), "Keyword.toString.text", keywordString, isLiteral, keywordType);
    }
    
    

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Keyword other = (Keyword) obj;
        if ((this.keywordString == null) ? (other.keywordString != null) : !this.keywordString.equals(other.keywordString)) {
            return false;
        }
        if (this.isLiteral != other.isLiteral) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (this.keywordString != null ? this.keywordString.hashCode() : 0);
        hash = 17 * hash + (this.isLiteral ? 1 : 0);
        return hash;
    }
}
