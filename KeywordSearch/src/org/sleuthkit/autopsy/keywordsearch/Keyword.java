/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A representation of a keyword for which to search. The search term for the
 * keyword may be either a literal term, with or without wildcards, or a regex.
 *
 * It is currently possible to optionally associate an artifact attribute type
 * with a keyword. This feature was added to support an initial implementation
 * of account number search and may be removed in the future.
 */
class Keyword {

    private String term;
    private boolean isLiteral;
    private boolean isWholeword;
    private BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType;

    /**
     * Constructs a representation of a keyword for which to search. The search
     * term for the keyword may be either a literal term without wildcards or a
     * regex.
     *
     * @param term      The search term for the keyword.
     * @param isLiteral Whether or not the search term is a literal term instead
     *                  of a regex. If the term is literal, this constructor
     *                  assumes that it does not include wildcards.
     */
    Keyword(String term, boolean isLiteral) {
        this.term = term;
        this.isLiteral = isLiteral;
        this.isWholeword = true;
    }

    /**
     * Constructs a representation of a keyword for which to search. The search
     * term may be either a literal term, with or without wildcards, or a regex.
     *
     * @param term           The search term.
     * @param isLiteral      Whether or not the search term is a literal term
     *                       instead of a regex.
     * @param hasNoWildcards Whether or not the search term, if it is a literal
     *                       search term, includes wildcards.
     */
    Keyword(String term, boolean isLiteral, boolean hasNoWildcards) {
        this.term = term;
        this.isLiteral = isLiteral;
        this.isWholeword = hasNoWildcards;
    }

    /**
     * Constructs a representation of a keyword for which to search, for the
     * purpose of finding a specific artifact attribute. The search term may be
     * either a literal term, with or without wildcards, or a regex.
     *
     * The association of an artifact attribute type with a keyword was added to
     * support an initial implementation of account number search and may be
     * removed in the future.
     *
     * @param term           The search term.
     * @param isLiteral      Whether or not the search term is a literal term
     *                       instead of a regex.
     * @param hasNoWildcards Whether or not the search term, if it is a literal
     *                       search term, includes wildcards.
     * @param keywordType    The artifact attribute type.
     */
    Keyword(String term, boolean isLiteral, BlackboardAttribute.ATTRIBUTE_TYPE keywordType) {
        this(term, isLiteral);
        this.artifactAtrributeType = keywordType;
    }

    /**
     * Gets the search term for the keyword, which may be either a literal term,
     * with or without wild cards, or a regex.
     *
     * @return The search term.
     */
    String getSearchTerm() {
        return term;
    }

    /**
     * Indicates whether the search term for the keyword is a literal term, with
     * or without wildcards, or a regex.
     *
     * @return True or false.
     */
    boolean searchTermIsLiteral() {
        return isLiteral;
    }

    /**
     * Indicates whether or not the search term for the keyword, if it is a
     * literal term and not a regex, includes wildcards.
     *
     * @return True or false.
     */
    boolean searchTermHasWildcards() {
        return !isWholeword;
    }

    /**
     * Sets the artifact attribute type associated with the keyword, if any.
     *
     * The association of an artifact attribute type with the keyword was added
     * to support an initial implementation of account number search and may be
     * removed in the future.
     *
     * @param artifactAtrributeType
     */
    void setArtifactAttributeType(BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType) {
        this.artifactAtrributeType = artifactAtrributeType;
    }

    /**
     * Gets the artifact attribute type associated with the keyword, if any.
     *
     * The association of an artifact attribute type with the keyword was added
     * to support an initial implementation of account number search and may be
     * removed in the future.
     *
     * @return A attribute type object or null.
     */
    BlackboardAttribute.ATTRIBUTE_TYPE getArtifactAttributeType() {
        return this.artifactAtrributeType;
    }

    @Override
    public String toString() {
        return String.format("Keyword{term='%s', isLiteral=%s, artifactAtrributeType=%s}", term, isLiteral, artifactAtrributeType);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Keyword other = (Keyword) obj;
        if ((this.term == null) ? (other.term != null) : !this.term.equals(other.term)) {
            return false;
        }
        return (this.isLiteral == other.isLiteral);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + (this.term != null ? this.term.hashCode() : 0);
        hash = 17 * hash + (this.isLiteral ? 1 : 0);
        return hash;
    }

}
