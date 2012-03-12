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

/**
 * Representation of keyword query as input by user
 */
package org.sleuthkit.autopsy.keywordsearch;

import org.sleuthkit.datamodel.BlackboardAttribute;

public class Keyword {

    private String query;
    private boolean isLiteral;
    private BlackboardAttribute.ATTRIBUTE_TYPE keywordType = null;

    Keyword(String query, boolean isLiteral) {
        this.query = query;
        this.isLiteral = isLiteral;
    }
    
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

    String getQuery() {
        return query;
    }

    boolean isLiteral() {
        return isLiteral;
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
        if ((this.query == null) ? (other.query != null) : !this.query.equals(other.query)) {
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
        hash = 17 * hash + (this.query != null ? this.query.hashCode() : 0);
        hash = 17 * hash + (this.isLiteral ? 1 : 0);
        return hash;
    }
}
