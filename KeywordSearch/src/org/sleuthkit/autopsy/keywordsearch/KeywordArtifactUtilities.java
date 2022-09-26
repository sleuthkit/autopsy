/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import static org.sleuthkit.autopsy.keywordsearch.KeywordSearchSettings.MODULE_NAME;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.CREDIT_CARD_TRACK1_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.CREDIT_CARD_TRACK2_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.KEYWORD_SEARCH_DOCUMENT_ID;
import static org.sleuthkit.autopsy.keywordsearch.RegexQuery.LOGGER;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class KeywordArtifactUtilities {
    
    private KeywordArtifactUtilities() {}
    
    /**
     * Adds a keyword hit artifact for a given keyword hit.
     *
     * @param content      The text source object for the hit.
     * @param foundKeyword The keyword that was found by the search, this may be
     *                     different than the Keyword that was searched if, for
     *                     example, it was a RegexQuery.
     * @param hit          The keyword hit.
     * @param snippet      A snippet from the text that contains the hit.
     * @param listName     The name of the keyword list that contained the
     *                     keyword for which the hit was found.
     *
     *
     * @return The newly created artifact or null if there was a problem
     *         creating it.
     */
    static BlackboardArtifact createKeywordHitArtifact(Content content, Keyword originalKeyword, Keyword foundKeyword, KeywordHit hit, String snippet, String listName, Long ingestJobId) {
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        if (content == null) {
            LOGGER.log(Level.WARNING, "Error adding artifact for keyword hit to blackboard"); //NON-NLS
            return null;
        }

        /*
         * Credit Card number hits a re handled differently
         */
        if (originalKeyword.getArtifactAttributeType() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            createCCNAccount(content, originalKeyword, foundKeyword, hit, snippet, listName, ingestJobId);
            return null;
        }

        /*
         * Create a "plain vanilla" keyword hit artifact with keyword and regex
         * attributes
         */
        Collection<BlackboardAttribute> attributes = new ArrayList<>();

        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, foundKeyword.getSearchTerm()));
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, originalKeyword.getSearchTerm()));

        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }

        hit.getArtifactID().ifPresent(artifactID
                -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactID))
        );

        if (originalKeyword.searchTermIsLiteral()) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.SUBSTRING.ordinal()));
        } else {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.REGEX.ordinal()));
        }

        try {
            return content.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_KEYWORD_HIT, Score.SCORE_LIKELY_NOTABLE, 
                    null, listName, null, attributes)
                    .getAnalysisResult();
        } catch (TskCoreException e) {
            LOGGER.log(Level.SEVERE, "Error adding bb attributes for terms search artifact", e); //NON-NLS
            return null;
        }
    }

    static void createCCNAccount(Content content, Keyword originalKeyword, Keyword foundKeyword, KeywordHit hit, String snippet, String listName, Long ingestJobId) {

        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        if (originalKeyword.getArtifactAttributeType() != BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            LOGGER.log(Level.SEVERE, "Keyword hit is not a credit card number"); //NON-NLS
            return;
        }
        /*
         * Create a credit card account with attributes parsed from the snippet
         * for the hit and looked up based on the parsed bank identifcation
         * number.
         */
        List<BlackboardAttribute> attributes = new ArrayList<>();

        Map<BlackboardAttribute.Type, BlackboardAttribute> parsedTrackAttributeMap = new HashMap<>();
        Matcher matcher = CREDIT_CARD_TRACK1_PATTERN.matcher(hit.getSnippet());
        if (matcher.find()) {
            parseTrack1Data(parsedTrackAttributeMap, matcher);
        }
        matcher = CREDIT_CARD_TRACK2_PATTERN.matcher(hit.getSnippet());
        if (matcher.find()) {
            parseTrack2Data(parsedTrackAttributeMap, matcher);
        }
        final BlackboardAttribute ccnAttribute = parsedTrackAttributeMap.get(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER));
        if (ccnAttribute == null || StringUtils.isBlank(ccnAttribute.getValueString())) {

            if (hit.isArtifactHit()) {
                LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for artifact keyword hit: term = %s, snippet = '%s', artifact id = %d", foundKeyword.getSearchTerm(), hit.getSnippet(), hit.getArtifactID().get())); //NON-NLS
            } else {
                try {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s', object id = %d", foundKeyword.getSearchTerm(), hit.getSnippet(), hit.getContentID())); //NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s' ", foundKeyword.getSearchTerm(), hit.getSnippet())); //NON-NLS
                    LOGGER.log(Level.SEVERE, "There was a error getting contentID for keyword hit.", ex); //NON-NLS
                }
            }
            return;
        }
        attributes.addAll(parsedTrackAttributeMap.values());

        /*
         * Look up the bank name, scheme, etc. attributes for the bank
         * indentification number (BIN).
         */
        final int bin = Integer.parseInt(ccnAttribute.getValueString().substring(0, 8));
        CreditCards.BankIdentificationNumber binInfo = CreditCards.getBINInfo(bin);
        if (binInfo != null) {
            binInfo.getScheme().ifPresent(scheme
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_SCHEME, MODULE_NAME, scheme)));
            binInfo.getCardType().ifPresent(cardType
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_TYPE, MODULE_NAME, cardType)));
            binInfo.getBrand().ifPresent(brand
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BRAND_NAME, MODULE_NAME, brand)));
            binInfo.getBankName().ifPresent(bankName
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BANK_NAME, MODULE_NAME, bankName)));
            binInfo.getBankPhoneNumber().ifPresent(phoneNumber
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, MODULE_NAME, phoneNumber)));
            binInfo.getBankURL().ifPresent(url
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, MODULE_NAME, url)));
            binInfo.getCountry().ifPresent(country
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNTRY, MODULE_NAME, country)));
            binInfo.getBankCity().ifPresent(city
                    -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CITY, MODULE_NAME, city)));
        }

        /*
         * If the hit is from unused or unallocated space, record the Solr
         * document id to support showing just the chunk that contained the hit.
         */
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            if (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS
                    || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
                attributes.add(new BlackboardAttribute(KEYWORD_SEARCH_DOCUMENT_ID, MODULE_NAME, hit.getSolrDocumentId()));
            }
        }

        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }

        hit.getArtifactID().ifPresent(artifactID
                -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactID))
        );

        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.REGEX.ordinal()));

        /*
         * Create an account instance.
         */
        try {
            Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.CREDIT_CARD, 
                    ccnAttribute.getValueString(), MODULE_NAME, content, attributes, ingestJobId);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Error creating CCN account instance", ex); //NON-NLS
        }

    }
    
    /**
     * Parses the track 2 data from the snippet for a credit card account number
     * hit and turns them into artifact attributes.
     *
     * @param attributesMap A map of artifact attribute objects, used to avoid
     *                      creating duplicate attributes.
     * @param matcher       A matcher for the snippet.
     */
    static private void parseTrack2Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributesMap, Matcher matcher) {
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_LRC, "LRC", matcher);
    }

    /**
     * Parses the track 1 data from the snippet for a credit card account number
     * hit and turns them into artifact attributes. The track 1 data has the
     * same fields as the track two data, plus the account holder's name.
     *
     * @param attributeMap A map of artifact attribute objects, used to avoid
     *                     creating duplicate attributes.
     * @param matcher      A matcher for the snippet.
     */
    static private void parseTrack1Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Matcher matcher) {
        parseTrack2Data(attributeMap, matcher);
        addAttributeIfNotAlreadyCaptured(attributeMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
    }

    /**
     * Creates an attribute of the the given type to the given artifact with a
     * value parsed from the snippet for a credit account number hit.
     *
     * @param attributeMap A map of artifact attribute objects, used to avoid
     *                     creating duplicate attributes.
     * @param attrType     The type of attribute to create.
     * @param groupName    The group name of the regular expression that was
     *                     used to parse the attribute data.
     * @param matcher      A matcher for the snippet.
     *
     */
    static private void addAttributeIfNotAlreadyCaptured(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, BlackboardAttribute.ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);

        if (!attributeMap.containsKey(type)) {
            String value = matcher.group(groupName);
            if (attrType.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER)) {
                attributeMap.put(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD),
                        new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, value));
                value = CharMatcher.anyOf(" -").removeFrom(value);
            }

            if (StringUtils.isNotBlank(value)) {
                attributeMap.put(type, new BlackboardAttribute(attrType, MODULE_NAME, value));
            }
        }
    }
}
