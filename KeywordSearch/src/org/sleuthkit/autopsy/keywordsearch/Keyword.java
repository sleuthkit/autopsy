/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.text.Normalizer;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * A representation of a keyword for which to search. The search term for the
 * keyword may be either a literal term, to be treated as either a whole word or
 * a substring, or a regex.
 *
 * It is currently possible to optionally associate an artifact attribute type
 * with a keyword. This feature was added to support an initial implementation
 * of account number search and may be removed in the future.
 */
public class Keyword {

    private String searchTerm;
    private boolean isLiteral;
    private boolean isWholeWord;
    private BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType;
    private final String listName;
    /*
     * For substring searches, original search term (e.g. "pass" or "enger") can
     * be different from the search term (e.g. "passenger") that is found and
     * used (e.g. for highlighting purposes).
     */
    private final String originalTerm;

    /**
     * Constructs a representation of a keyword for which to search. The search
     * term may be either a literal term, to be treated as either a whole word
     * or as a substring, or a regex.
     *
     * @param searchTerm  The search term.
     * @param isLiteral   Whether or not the search term is a literal term,
     *                    instead of a regex.
     * @param isWholeWord Whether or not the search term, if it is a literal
     *                    search term, should be treated as a whole word rather
     *                    than a substring.
     */
    Keyword(String searchTerm, boolean isLiteral, boolean isWholeWord) {
        this(searchTerm, isLiteral, isWholeWord, "", searchTerm);
    }

    /**
     * Constructs a representation of a keyword for which to search. The search
     * term may be either a literal term, to be treated as either a whole word
     * or as a substring, or a regex.
     *
     * NOTE: The addition of keyword list name and original search term was
     * added to facilitate proper de-duping of results of periodic keyword
     * searches that does not lose any keyword hits. Without this addition when
     * using substring search feature during ingest, if there are multiple
     * searches on different keyword lists that produce the same keyword hit,
     * that hit is only going to be displayed in results of one of the list. For
     * example, two substring searches, such as "pass" and "enger", will be
     * missing one copy of any shared entries (i.e., "passenger" will only show
     * up on one list). See JIRA story 2495.
     *
     * @param searchTerm   The search term.
     * @param isLiteral    Whether or not the search term is a literal term,
     *                     instead of a regex.
     * @param isWholeWord  Whether or not the search term, if it is a literal
     *                     search term, should be treated as a whole word rather
     *                     than a substring.
     * @param listName     Keyword list name.
     * @param originalTerm The original search term that was entered into the
     *                     keyword list. For substring searches, original search
     *                     term (e.g. "pass" or "enger") can be different from
     *                     the search term (e.g. "passenger") that is found and
     *                     used (e.g. for highlighting purposes).
     */
    Keyword(String searchTerm, boolean isLiteral, boolean isWholeWord, String listName, String originalTerm) {
        this.searchTerm = Normalizer.normalize(searchTerm, Normalizer.Form.NFKC);
        this.isLiteral = isLiteral;
        this.isWholeWord = isWholeWord;
        this.listName = listName;
        this.originalTerm = originalTerm;
        
    }

    /**
     * Constructs a representation of a keyword for which to search, for the
     * purpose of finding a specific artifact attribute. The search term may be
     * either a literal term, to be treated as a whole word, or a regex.
     *
     * The association of an artifact attribute type with a keyword was added to
     * support an initial implementation of account number search and may be
     * removed in the future.
     *
     * @param searchTerm  The search term.
     * @param isLiteral   Whether or not the search term is a literal term, to
     *                    be treated as a whole word, instead of a regex.
     * @param keywordType The artifact attribute type.
     */
    Keyword(String searchTerm, boolean isLiteral, BlackboardAttribute.ATTRIBUTE_TYPE artifactAtrributeType) {
        this(searchTerm, isLiteral, true);
        this.artifactAtrributeType = artifactAtrributeType;
    }

    /**
     * Gets the search term for the keyword, which may be either a literal term
     * or a regex.
     *
     * @return The search term.
     */
    public String getSearchTerm() {
        return searchTerm;
    }

    /**
     * Indicates whether the search term for the keyword is a literal term or a
     * regex.
     *
     * @return True or false.
     */
    public boolean searchTermIsLiteral() {
        return isLiteral;
    }

    /**
     * Indicates whether or not the search term for the keyword, if it is a
     * literal term and not a regex, will be treated as a whole word or as a
     * substring.
     *
     * @return True or false.
     */
    public boolean searchTermIsWholeWord() {
        return isWholeWord;
    }

    String getSearchTermType() {
        if (isLiteral) {
            if (isWholeWord) {
                return NbBundle.getMessage(NewKeywordPanel.class, "NewKeywordPanel.exactButton.text");
            } else {
                return NbBundle.getMessage(NewKeywordPanel.class, "NewKeywordPanel.substringButton.text");
            }
        } else {
            return NbBundle.getMessage(NewKeywordPanel.class, "NewKeywordPanel.regexButton.text");
        }
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
        return String.format("Keyword{searchTerm='%s', isLiteral=%s, isWholeWord=%s}", searchTerm, isLiteral, isWholeWord);
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
        return (this.searchTerm.equals(other.getSearchTerm())
                && this.isLiteral == other.searchTermIsLiteral()
                && this.isWholeWord == other.searchTermIsWholeWord()
                && this.listName.equals(other.getListName())
                && this.originalTerm.equals(other.getOriginalTerm()));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + this.searchTerm.hashCode();
        hash = 17 * hash + (this.isLiteral ? 1 : 0);
        hash = 17 * hash + (this.isWholeWord ? 1 : 0);
        return hash;
    }

    /**
     * @return the listName
     */
    String getListName() {
        return listName;
    }

    /**
     * @return the originalTerm
     */
    String getOriginalTerm() {
        return originalTerm;
    }

}
