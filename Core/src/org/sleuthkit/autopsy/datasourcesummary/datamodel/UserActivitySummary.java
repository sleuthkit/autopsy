/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

/**
 * Provides summary information about user activity in a datasource. At this
 * time, the data being provided for domains is fictitious and is done as a
 * placeholder.
 */
public class UserActivitySummary {

    /**
     * Functions that determine the folder name of a list of path elements. If
     * not matched, function returns null.
     */
    private static final List<Function<List<String>, String>> SHORT_FOLDER_MATCHERS = Arrays.asList(
            // handle Program Files and Program Files (x86) - if true, return the next folder
            (pathList) -> {
                if (pathList.size() < 2) {
                    return null;
                }

                String rootParent = pathList.get(0).toUpperCase();
                if ("PROGRAM FILES".equals(rootParent) || "PROGRAM FILES (X86)".equals(rootParent)) {
                    return pathList.get(1);
                } else {
                    return null;
                }
            },
            // if there is a folder named "APPLICATION DATA" or "APPDATA"
            (pathList) -> {
                for (String pathEl : pathList) {
                    String uppered = pathEl.toUpperCase();
                    if ("APPLICATION DATA".equals(uppered) || "APPDATA".equals(uppered)) {
                        return "AppData";
                    }
                }
                return null;
            }
    );

    private static final BlackboardArtifact.Type TYPE_DEVICE_ATTACHED = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_DEVICE_ATTACHED);
    private static final BlackboardArtifact.Type TYPE_WEB_HISTORY = new BlackboardArtifact.Type(ARTIFACT_TYPE.TSK_WEB_HISTORY);

    private static final BlackboardAttribute.Type TYPE_DATETIME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME);
    private static final BlackboardAttribute.Type TYPE_DATETIME_ACCESSED = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED);
    private static final BlackboardAttribute.Type TYPE_DEVICE_ID = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_ID);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MAKE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE);
    private static final BlackboardAttribute.Type TYPE_DEVICE_MODEL = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL);
    private static final BlackboardAttribute.Type TYPE_MESSAGE_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE);
    private static final BlackboardAttribute.Type TYPE_TEXT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TEXT);
    private static final BlackboardAttribute.Type TYPE_DATETIME_RCVD = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD);
    private static final BlackboardAttribute.Type TYPE_DATETIME_SENT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_SENT);
    private static final BlackboardAttribute.Type TYPE_DATETIME_START = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_START);
    private static final BlackboardAttribute.Type TYPE_DATETIME_END = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_END);
    private static final BlackboardAttribute.Type TYPE_DOMAIN = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN);
    private static final BlackboardAttribute.Type TYPE_PROG_NAME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME);
    private static final BlackboardAttribute.Type TYPE_PATH = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH);
    private static final BlackboardAttribute.Type TYPE_COUNT = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COUNT);

    private static final String NTOS_BOOT_IDENTIFIER = "NTOSBOOT";
    private static final String WINDOWS_PREFIX = "/WINDOWS";

    private static final Comparator<TopAccountResult> TOP_ACCOUNT_RESULT_DATE_COMPARE = (a, b) -> a.getLastAccessed().compareTo(b.getLastAccessed());
    private static final Comparator<TopWebSearchResult> TOP_WEBSEARCH_RESULT_DATE_COMPARE = (a, b) -> a.getLastAccessed().compareTo(b.getLastAccessed());

    /**
     * Sorts TopProgramsResults pushing highest run time count then most recent
     * run and then the program name that comes earliest in the alphabet.
     */
    private static final Comparator<TopProgramsResult> TOP_PROGRAMS_RESULT_COMPARE = (a, b) -> {
        // first priority for sorting is the run times 
        // if non-0, this is the return value for the comparator
        int runTimesCompare = nullableCompare(a.getRunTimes(), b.getRunTimes());
        if (runTimesCompare != 0) {
            return -runTimesCompare;
        }

        // second priority for sorting is the last run date
        // if non-0, this is the return value for the comparator
        int lastRunCompare = nullableCompare(
                a.getLastAccessed() == null ? null : a.getLastAccessed().getTime(),
                b.getLastAccessed() == null ? null : b.getLastAccessed().getTime());

        if (lastRunCompare != 0) {
            return -lastRunCompare;
        }

        // otherwise sort alphabetically
        return (a.getProgramName() == null ? "" : a.getProgramName())
                .compareToIgnoreCase((b.getProgramName() == null ? "" : b.getProgramName()));
    };

    private static final Set<String> DEVICE_EXCLUDE_LIST = new HashSet<>(Arrays.asList("ROOT_HUB", "ROOT_HUB20"));
    private static final Set<String> DOMAIN_EXCLUDE_LIST = new HashSet<>(Arrays.asList("127.0.0.1", "LOCALHOST"));

    private static final long MS_PER_DAY = 1000 * 60 * 60 * 24;
    private static final long DOMAIN_WINDOW_DAYS = 30;
    private static final long DOMAIN_WINDOW_MS = DOMAIN_WINDOW_DAYS * MS_PER_DAY;

    private final SleuthkitCaseProvider caseProvider;
    private final TextTranslationService translationService;
    private final java.util.logging.Logger logger;

    /**
     * Main constructor.
     */
    public UserActivitySummary() {
        this(SleuthkitCaseProvider.DEFAULT, TextTranslationService.getInstance(),
                org.sleuthkit.autopsy.coreutils.Logger.getLogger(UserActivitySummary.class.getName()));
    }

    /**
     * Main constructor with external dependencies specified. This constructor
     * is designed with unit testing in mind since mocked dependencies can be
     * utilized.
     *
     * @param provider The object providing the current SleuthkitCase.
     * @param translationService The translation service.
     * @param logger The logger to use.
     */
    public UserActivitySummary(
            SleuthkitCaseProvider provider,
            TextTranslationService translationService,
            java.util.logging.Logger logger) {

        this.caseProvider = provider;
        this.translationService = translationService;
        this.logger = logger;
    }

    /**
     * Throws an IllegalArgumentException if count <= 0.
     *
     * @param count The count being checked.
     */
    private static void assertValidCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be greater than 0");
        }
    }
    
    /**
     * Determines a short folder name if any. Otherwise, returns empty string.
     *
     * @param strPath The string path.
     * @param applicationName The application name.
     *
     * @return The short folder name or empty string if not found.
     */
    public static String getShortFolderName(String strPath, String applicationName) {
        if (strPath == null) {
            return "";
        }

        List<String> pathEls = new ArrayList<>(Arrays.asList(applicationName));

        File file = new File(strPath);
        while (file != null && org.apache.commons.lang.StringUtils.isNotBlank(file.getName())) {
            pathEls.add(file.getName());
            file = file.getParentFile();
        }

        Collections.reverse(pathEls);

        for (Function<List<String>, String> matchEntry : SHORT_FOLDER_MATCHERS) {
            String result = matchEntry.apply(pathEls);
            if (org.apache.commons.lang.StringUtils.isNotBlank(result)) {
                return result;
            }
        }

        return "";
    }    

    /**
     * Gets a list of recent domains based on the datasource.
     *
     * @param dataSource The datasource to query for recent domains.
     * @param count      The max count of items to return.
     *
     * @return The list of items retrieved from the database.
     *
     * @throws InterruptedException
     */
    public List<TopDomainsResult> getRecentDomains(DataSource dataSource, int count) throws TskCoreException, SleuthkitCaseProviderException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        Pair<Long, Map<String, List<Pair<BlackboardArtifact, Long>>>> mostRecentAndGroups = getDomainGroupsAndMostRecent(dataSource);
        // if no recent domains, return accordingly
        if (mostRecentAndGroups.getKey() == null || mostRecentAndGroups.getValue().size() == 0) {
            return Collections.emptyList();
        }

        final long mostRecentMs = mostRecentAndGroups.getLeft();
        Map<String, List<Pair<BlackboardArtifact, Long>>> groups = mostRecentAndGroups.getRight();

        return groups.entrySet().stream()
                .map(entry -> getDomainsResult(entry.getKey(), entry.getValue(), mostRecentMs))
                .filter(result -> result != null)
                // sort by number of visit times in those 30 days (max to min)
                .sorted((a, b) -> -Long.compare(a.getVisitTimes(), b.getVisitTimes()))
                // limit the result number to the parameter provided
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Creates a TopDomainsResult from data or null if no visit date exists
     * within DOMAIN_WINDOW_MS of mostRecentMs.
     *
     * @param domain       The domain.
     * @param visits       The list of the artifact and its associated time in
     *                     milliseconds.
     * @param mostRecentMs The most recent visit of any domain.
     *
     * @return The TopDomainsResult or null if no visits to this domain within
     *         30 days of mostRecentMs.
     */
    private TopDomainsResult getDomainsResult(String domain, List<Pair<BlackboardArtifact, Long>> visits, long mostRecentMs) {
        long visitCount = 0;
        Long thisMostRecentMs = null;
        BlackboardArtifact thisMostRecentArtifact = null;

        for (Pair<BlackboardArtifact, Long> visitInstance : visits) {
            BlackboardArtifact artifact = visitInstance.getLeft();
            Long visitMs = visitInstance.getRight();
            // make sure that visit is within window of mostRecentMS; otherwise skip it.
            if (visitMs == null || visitMs + DOMAIN_WINDOW_MS < mostRecentMs) {
                continue;
            }

            // if visit is within window, increment the count and get most recent
            visitCount++;
            if (thisMostRecentMs == null || visitMs > thisMostRecentMs) {
                thisMostRecentMs = visitMs;
                thisMostRecentArtifact = artifact;
            }
            thisMostRecentMs = getMax(thisMostRecentMs, visitMs);
        }

        // if there are no visits within the window, return null
        if (visitCount <= 0 || thisMostRecentMs == null) {
            return null;
        } else {
            // create a top domain result with the domain, count, and most recent visit date
            return new TopDomainsResult(domain, visitCount, new Date(thisMostRecentMs), thisMostRecentArtifact);
        }
    }

    /**
     * Queries TSK_WEB_HISTORY artifacts and returning the latest web history
     * date accessed and a mapping of domains to all of their visits.
     *
     * @param dataSource The datasource.
     *
     * @return A tuple where the first value is the latest web history accessed
     *         date in milliseconds and the second value maps normalized
     *         (lowercase; trimmed) domain names to when those domains were
     *         visited and the relevant artifact.
     *
     * @throws TskCoreException
     * @throws SleuthkitCaseProviderException
     */
    private Pair<Long, Map<String, List<Pair<BlackboardArtifact, Long>>>> getDomainGroupsAndMostRecent(DataSource dataSource) throws TskCoreException, SleuthkitCaseProviderException {
        List<BlackboardArtifact> artifacts = DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_WEB_HISTORY,
                dataSource, TYPE_DATETIME_ACCESSED, DataSourceInfoUtilities.SortOrder.DESCENDING, 0);

        Long mostRecentMs = null;
        Map<String, List<Pair<BlackboardArtifact, Long>>> domainVisits = new HashMap<>();

        for (BlackboardArtifact art : artifacts) {
            Long artifactDateSecs = DataSourceInfoUtilities.getLongOrNull(art, TYPE_DATETIME_ACCESSED);
            String domain = DataSourceInfoUtilities.getStringOrNull(art, TYPE_DOMAIN);

            // if there isn't a last access date or domain for this artifact, it can be ignored.
            // Also, ignore the loopback address.
            if (artifactDateSecs == null || StringUtils.isBlank(domain) || DOMAIN_EXCLUDE_LIST.contains(domain.toUpperCase().trim())) {
                continue;
            }

            Long artifactDateMs = artifactDateSecs * 1000;

            // update the most recent visit date overall
            mostRecentMs = getMax(mostRecentMs, artifactDateMs);

            //Normalize the domain to lower case.
            domain = domain.toLowerCase().trim();

            // add this visit date to the list of dates for the domain
            List<Pair<BlackboardArtifact, Long>> domainVisitList = domainVisits.get(domain);
            if (domainVisitList == null) {
                domainVisitList = new ArrayList<>();
                domainVisits.put(domain, domainVisitList);
            }

            domainVisitList.add(Pair.of(art, artifactDateMs));
        }

        return Pair.of(mostRecentMs, domainVisits);
    }

    /**
     * Returns the maximum value given two longs handling possible null values.
     *
     * @param num1 The first number.
     * @param num2 The second number.
     *
     * @return The maximum non-null number or null if both numbers are null.
     */
    private static Long getMax(Long num1, Long num2) {
        if (num1 == null) {
            return num2;
        } else if (num2 == null) {
            return num1;
        } else {
            return num2 > num1 ? num2 : num1;
        }
    }

    /**
     * Attempts to obtain a web search result record from a blackboard artifact.
     *
     * @param artifact The artifact.
     *
     * @return The TopWebSearchResult or null if the search string or date
     *         accessed cannot be determined.
     */
    private static TopWebSearchResult getWebSearchResult(BlackboardArtifact artifact) {
        String searchString = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_TEXT);
        Date dateAccessed = DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME_ACCESSED);
        return (StringUtils.isNotBlank(searchString) && dateAccessed != null)
                ? new TopWebSearchResult(searchString, dateAccessed, artifact)
                : null;
    }

    /**
     * Retrieves most recent web searches by most recent date grouped by search
     * term.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent web searches where most recent search
     *         appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopWebSearchResult> getMostRecentWebSearches(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        // get the artifacts
        List<BlackboardArtifact> webSearchArtifacts = caseProvider.get().getBlackboard()
                .getArtifacts(ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID(), dataSource.getId());

        // group by search string (case insensitive)
        Collection<TopWebSearchResult> resultGroups = webSearchArtifacts
                .stream()
                // get items where search string and date is not null
                .map(UserActivitySummary::getWebSearchResult)
                // remove null records
                .filter(result -> result != null)
                // get the latest message for each search string
                .collect(Collectors.toMap(
                        (result) -> result.getSearchString().toUpperCase(),
                        result -> result,
                        (result1, result2) -> TOP_WEBSEARCH_RESULT_DATE_COMPARE.compare(result1, result2) >= 0 ? result1 : result2))
                .values();

        // get the most recent date for each search term
        List<TopWebSearchResult> results = resultGroups
                .stream()
                // get most recent searches first
                .sorted(TOP_WEBSEARCH_RESULT_DATE_COMPARE.reversed())
                .limit(count)
                // get as list
                .collect(Collectors.toList());

        // get translation if possible
        if (translationService.hasProvider()) {
            for (TopWebSearchResult result : results) {
                result.setTranslatedResult(getTranslationOrNull(result.getSearchString()));
            }
        }

        return results;
    }

    /**
     * Return the translation of the original text if possible and differs from
     * the original. Otherwise, return null.
     *
     * @param original The original text.
     *
     * @return The translated text or null if no translation can be determined
     * or exists.
     */
    private String getTranslationOrNull(String original) {
        if (!translationService.hasProvider() || StringUtils.isBlank(original)) {
            return null;
        }

        String translated = null;
        try {
            translated = translationService.translate(original);
        } catch (NoServiceProviderException | TranslationException ex) {
            logger.log(Level.WARNING, String.format("There was an error translating text: '%s'", original), ex);
        }

        // if there is no translation or the translation is the same as the original, return null.
        if (StringUtils.isBlank(translated)
                || translated.toUpperCase().trim().equals(original.toUpperCase().trim())) {

            return null;
        }

        return translated;
    }

    /**
     * Gives the most recent TopDeviceAttachedResult. If one is null, the other
     * is returned.
     *
     * @param r1 A result.
     * @param r2 Another result.
     *
     * @return The most recent one with a non-null date.
     */
    private TopDeviceAttachedResult getMostRecentDevice(TopDeviceAttachedResult r1, TopDeviceAttachedResult r2) {
        if (r2.getLastAccessed()== null) {
            return r1;
        }

        if (r1.getLastAccessed() == null) {
            return r2;
        }

        return r1.getLastAccessed().compareTo(r2.getLastAccessed()) >= 0 ? r1 : r2;
    }

    /**
     * Retrieves most recent devices used by most recent date attached.
     *
     * @param dataSource The data source.
     * @param count      The maximum number of records to be shown (must be >
     *                   0).
     *
     * @return The list of most recent devices attached where most recent device
     *         attached appears first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopDeviceAttachedResult> getRecentDevices(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        Collection<TopDeviceAttachedResult> results = DataSourceInfoUtilities.getArtifacts(caseProvider.get(), TYPE_DEVICE_ATTACHED,
                dataSource, TYPE_DATETIME, DataSourceInfoUtilities.SortOrder.DESCENDING, 0)
                .stream()
                .map(artifact -> {
                    return new TopDeviceAttachedResult(
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_ID),
                            DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME),
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MAKE),
                            DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_DEVICE_MODEL),
                            artifact
                    );
                })
                // remove Root Hub identifier
                .filter(result -> {
                    return result.getDeviceId() == null
                            || result.getDeviceModel() == null
                            || !DEVICE_EXCLUDE_LIST.contains(result.getDeviceModel().trim().toUpperCase());
                })
                .collect(Collectors.toMap(result -> result.getDeviceId(), result -> result, (r1, r2) -> getMostRecentDevice(r1, r2)))
                .values();

        return results.stream()
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Obtains a TopAccountResult from a TSK_MESSAGE blackboard artifact.
     *
     * @param artifact The artifact.
     *
     * @return The TopAccountResult or null if the account type or message date
     *         cannot be determined.
     */
    private static TopAccountResult getMessageAccountResult(BlackboardArtifact artifact) {
        String type = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_MESSAGE_TYPE);
        Date date = DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME);
        return (StringUtils.isNotBlank(type) && date != null)
                ? new TopAccountResult(type, date, artifact)
                : null;
    }

    /**
     * Obtains a TopAccountResult from a blackboard artifact. The date is
     * maximum of any found dates for attribute types provided.
     *
     * @param artifact    The artifact.
     * @param messageType The type of message this is.
     * @param dateAttrs   The date attribute types.
     *
     * @return The TopAccountResult or null if the account type or max date are
     *         not provided.
     */
    private static TopAccountResult getAccountResult(BlackboardArtifact artifact, String messageType, BlackboardAttribute.Type... dateAttrs) {
        String type = messageType;

        Date latestDate = null;
        if (dateAttrs != null) {
            latestDate = Stream.of(dateAttrs)
                    .map((attr) -> DataSourceInfoUtilities.getDateOrNull(artifact, attr))
                    .filter((date) -> date != null)
                    .max((a, b) -> a.compareTo(b))
                    .orElse(null);
        }

        return (StringUtils.isNotBlank(type) && latestDate != null)
                ? new TopAccountResult(type, latestDate, artifact)
                : null;
    }

    /**
     * Retrieves most recent account used by most recent date for a message
     * sent.
     *
     * @param dataSource The data source.
     * @param count The maximum number of records to be shown (must be > 0).
     *
     * @return The list of most recent accounts used where the most recent
     * account by last message sent occurs first.
     *
     * @throws
     * org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    @Messages({
        "DataSourceUserActivitySummary_getRecentAccounts_emailMessage=Email Message",
        "DataSourceUserActivitySummary_getRecentAccounts_calllogMessage=Call Log",})
    public List<TopAccountResult> getRecentAccounts(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        Stream<TopAccountResult> messageResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_MESSAGE.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> getMessageAccountResult(art));

        Stream<TopAccountResult> emailResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> {
                    return getAccountResult(
                            art,
                            Bundle.DataSourceUserActivitySummary_getRecentAccounts_emailMessage(),
                            TYPE_DATETIME_RCVD,
                            TYPE_DATETIME_SENT);
                });

        Stream<TopAccountResult> calllogResults = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_CALLLOG.getTypeID(), dataSource.getId())
                .stream()
                .map((art) -> {
                    return getAccountResult(
                            art,
                            Bundle.DataSourceUserActivitySummary_getRecentAccounts_calllogMessage(),
                            TYPE_DATETIME_START,
                            TYPE_DATETIME_END);
                });

        Stream<TopAccountResult> allResults = Stream.concat(messageResults, Stream.concat(emailResults, calllogResults));

        // get them grouped by account type        
        Collection<TopAccountResult> groupedResults = allResults
                // remove null records
                .filter(result -> result != null)
                // get these messages grouped by account type and get the most recent of each type
                .collect(Collectors.toMap(
                        result -> result.getAccountType(),
                        result -> result,
                        (result1, result2) -> TOP_ACCOUNT_RESULT_DATE_COMPARE.compare(result1, result2) >= 0 ? result1 : result2))
                .values();

        // get account type sorted by most recent date
        return groupedResults
                .stream()
                // get most recent accounts accessed
                .sorted(TOP_ACCOUNT_RESULT_DATE_COMPARE.reversed())
                // limit to count
                .limit(count)
                // get as list
                .collect(Collectors.toList());
    }

    /**
     * Creates a TopProgramsResult from a TSK_PROG_RUN blackboard artifact.
     *
     * @param artifact The TSK_PROG_RUN blackboard artifact.
     *
     * @return The generated TopProgramsResult.
     */
    private TopProgramsResult getTopProgramsResult(BlackboardArtifact artifact) {
        String programName = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_PROG_NAME);

        // ignore items with no name or a ntos boot identifier
        if (StringUtils.isBlank(programName) || NTOS_BOOT_IDENTIFIER.equalsIgnoreCase(programName)) {
            return null;
        }

        String path = DataSourceInfoUtilities.getStringOrNull(artifact, TYPE_PATH);

        // ignore windows directory
        if (StringUtils.startsWithIgnoreCase(path, WINDOWS_PREFIX)) {
            return null;
        }

        Integer count = DataSourceInfoUtilities.getIntOrNull(artifact, TYPE_COUNT);
        Long longCount = (count == null) ? null : (long) count;

        return new TopProgramsResult(
                programName,
                path,
                longCount,
                DataSourceInfoUtilities.getDateOrNull(artifact, TYPE_DATETIME),
                artifact
        );
    }

    /**
     * Retrieves the maximum date given two (possibly null) dates.
     *
     * @param date1 First date.
     * @param date2 Second date.
     *
     * @return The maximum non-null date or null if both items are null.
     */
    private static Date getMax(Date date1, Date date2) {
        if (date1 == null) {
            return date2;
        } else if (date2 == null) {
            return date1;
        } else {
            return date1.compareTo(date2) > 0 ? date1 : date2;
        }
    }

    /**
     * Returns the compare value favoring the higher non-null number.
     *
     * @param long1 First possibly null long.
     * @param long2 Second possibly null long.
     *
     * @return Returns the compare value: 1,0,-1 favoring the higher non-null
     * value.
     */
    private static int nullableCompare(Long long1, Long long2) {
        if (long1 == null && long2 == null) {
            return 0;
        } else if (long1 != null && long2 == null) {
            return 1;
        } else if (long1 == null && long2 != null) {
            return -1;
        }

        return Long.compare(long1, long2);
    }

    /**
     * Returns true if number is non-null and higher than 0.
     *
     * @param longNum The number.
     *
     * @return True if non-null and higher than 0.
     */
    private static boolean isPositiveNum(Long longNum) {
        return longNum != null && longNum > 0;
    }

    /**
     * Retrieves the top programs results for the given data source limited to
     * the count provided as a parameter. The highest run times are at the top
     * of the list. If that information isn't available the last run date is
     * used. If both, the last run date and the number of run times are
     * unavailable, the programs will be sorted alphabetically, the count will
     * be ignored and all items will be returned.
     *
     * @param dataSource The datasource. If the datasource is null, an empty
     *                   list will be returned.
     * @param count      The number of results to return. This value must be > 0
     *                   or an IllegalArgumentException will be thrown.
     *
     * @return The sorted list and limited to the count if last run or run count
     *         information is available on any item.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<TopProgramsResult> getTopPrograms(DataSource dataSource, int count) throws SleuthkitCaseProviderException, TskCoreException {
        assertValidCount(count);

        if (dataSource == null) {
            return Collections.emptyList();
        }

        // Get TopProgramsResults for each TSK_PROG_RUN artifact
        Collection<TopProgramsResult> results = caseProvider.get().getBlackboard().getArtifacts(ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID(), dataSource.getId())
                .stream()
                // convert to a TopProgramsResult object or null if missing critical information
                .map((art) -> getTopProgramsResult(art))
                // remove any null items
                .filter((res) -> res != null)
                // group by the program name and program path
                // The value will be a TopProgramsResult with the max run times 
                // and most recent last run date for each program name / program path pair.
                .collect(Collectors.toMap(
                        res -> Pair.of(
                                res.getProgramName() == null ? null : res.getProgramName().toUpperCase(),
                                res.getProgramPath() == null ? null : res.getProgramPath().toUpperCase()),
                        res -> res,
                        (res1, res2) -> {
                            Long maxRunTimes = getMax(res1.getRunTimes(), res2.getRunTimes());
                            Date maxDate = getMax(res1.getLastAccessed(), res2.getLastAccessed());
                            TopProgramsResult maxResult = TOP_PROGRAMS_RESULT_COMPARE.compare(res1, res2) >= 0 ? res1 : res2;
                            return new TopProgramsResult(
                                    maxResult.getProgramName(),
                                    maxResult.getProgramPath(),
                                    maxRunTimes,
                                    maxDate,
                                    maxResult.getArtifact());
                        })).values();

        List<TopProgramsResult> orderedResults = results.stream()
                .sorted(TOP_PROGRAMS_RESULT_COMPARE)
                .collect(Collectors.toList());

        // only limit the list to count if there is no last run date and no run times.
        if (!orderedResults.isEmpty()) {
            TopProgramsResult topResult = orderedResults.get(0);
            // if run times / last run information is available, the first item should have some value,
            // and then the items should be limited accordingly.
            if (isPositiveNum(topResult.getRunTimes())
                    || (topResult.getLastAccessed() != null && isPositiveNum(topResult.getLastAccessed().getTime()))) {
                return orderedResults.stream().limit(count).collect(Collectors.toList());
            }
        }

        // otherwise return the alphabetized list with no limit applied.
        return orderedResults;
    }

    /**
     * Base class including date of last access and the relevant blackboard
     * artifact.
     */
    public static class LastAccessedArtifact {

        private final Date lastAccessed;
        private final BlackboardArtifact artifact;

        /**
         * Main constructor.
         *
         * @param lastAccessed The date of last access.
         * @param artifact     The relevant blackboard artifact.
         */
        public LastAccessedArtifact(Date lastAccessed, BlackboardArtifact artifact) {
            this.lastAccessed = lastAccessed;
            this.artifact = artifact;
        }

        /**
         * @return The date of last access.
         */
        public Date getLastAccessed() {
            return lastAccessed;
        }

        /**
         * @return The associated artifact.
         */
        public BlackboardArtifact getArtifact() {
            return artifact;
        }
    }

    /**
     * Object containing information about a web search artifact.
     */
    public static class TopWebSearchResult extends LastAccessedArtifact {

        private final String searchString;
        private String translatedResult;

        /**
         * Main constructor.
         *
         * @param searchString The search string.
         * @param dateAccessed The latest date searched.
         * @param artifact     The relevant blackboard artifact.
         */
        public TopWebSearchResult(String searchString, Date dateAccessed, BlackboardArtifact artifact) {
            super(dateAccessed, artifact);
            this.searchString = searchString;
        }

        /**
         * @return The translated result if one was determined.
         */
        public String getTranslatedResult() {
            return translatedResult;
        }

        /**
         * Sets the translated result for this web search.
         *
         * @param translatedResult The translated result.
         */
        public void setTranslatedResult(String translatedResult) {
            this.translatedResult = translatedResult;
        }

        /**
         * @return The search string.
         */
        public String getSearchString() {
            return searchString;
        }
    }

    /**
     * A record of a device attached.
     */
    public static class TopDeviceAttachedResult extends LastAccessedArtifact {

        private final String deviceId;
        private final String deviceMake;
        private final String deviceModel;

        /**
         * Main constructor.
         *
         * @param deviceId     The device id.
         * @param dateAccessed The date last attached.
         * @param deviceMake   The device make.
         * @param deviceModel  The device model.
         * @param artifact     The relevant blackboard artifact.
         */
        public TopDeviceAttachedResult(String deviceId, Date dateAccessed, String deviceMake, String deviceModel, BlackboardArtifact artifact) {
            super(dateAccessed, artifact);
            this.deviceId = deviceId;
            this.deviceMake = deviceMake;
            this.deviceModel = deviceModel;
        }

        /**
         * @return The device id.
         */
        public String getDeviceId() {
            return deviceId;
        }

        /**
         * @return The device make.
         */
        public String getDeviceMake() {
            return deviceMake;
        }

        /**
         * @return The device model.
         */
        public String getDeviceModel() {
            return deviceModel;
        }
    }

    /**
     * A record of an account and the last time it was used determined by
     * messages.
     */
    public static class TopAccountResult extends LastAccessedArtifact {

        private final String accountType;

        /**
         * Main constructor.
         *
         * @param accountType The account type.
         * @param lastAccess  The date the account was last accessed.
         * @param artifact    The artifact indicating last access.
         */
        public TopAccountResult(String accountType, Date lastAccess, BlackboardArtifact artifact) {
            super(lastAccess, artifact);
            this.accountType = accountType;
        }

        /**
         * @return The account type.
         */
        public String getAccountType() {
            return accountType;
        }
    }

    /**
     * Describes a result of a program run on a datasource.
     */
    public static class TopDomainsResult extends LastAccessedArtifact {

        private final String domain;
        private final Long visitTimes;

        /**
         * Describes a top domain result.
         *
         * @param domain     The domain.
         * @param visitTimes The number of times it was visited.
         * @param lastVisit  The date of the last visit.
         * @param artifact   The relevant blackboard artifact.
         */
        public TopDomainsResult(String domain, Long visitTimes, Date lastVisit, BlackboardArtifact artifact) {
            super(lastVisit, artifact);
            this.domain = domain;
            this.visitTimes = visitTimes;
        }

        /**
         * @return The domain for the result.
         */
        public String getDomain() {
            return domain;
        }

        /**
         * @return The number of times this site is visited.
         */
        public Long getVisitTimes() {
            return visitTimes;
        }
    }

    /**
     * Describes a result of a program run on a datasource.
     */
    public static class TopProgramsResult extends LastAccessedArtifact {

        private final String programName;
        private final String programPath;
        private final Long runTimes;

        /**
         * Main constructor.
         *
         * @param programName The name of the program.
         * @param programPath The path of the program.
         * @param runTimes    The number of runs.
         * @param artifact    The relevant blackboard artifact.
         */
        TopProgramsResult(String programName, String programPath, Long runTimes, Date lastRun, BlackboardArtifact artifact) {
            super(lastRun, artifact);
            this.programName = programName;
            this.programPath = programPath;
            this.runTimes = runTimes;
        }

        /**
         * @return The name of the program
         */
        public String getProgramName() {
            return programName;
        }

        /**
         * @return The path of the program.
         */
        public String getProgramPath() {
            return programPath;
        }

        /**
         * @return The number of run times or null if not present.
         */
        public Long getRunTimes() {
            return runTimes;
        }
    }
}
