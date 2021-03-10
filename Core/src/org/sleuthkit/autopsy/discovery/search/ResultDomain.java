/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.util.Set;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskData;

/**
 * Container for domains that holds all necessary data for grouping and sorting.
 */
public class ResultDomain extends Result {

    private final String domain;
    private final Long activityStart;
    private final Long activityEnd;
    private final Long totalPageViews;
    private final Long pageViewsInLast60;
    private final Long filesDownloaded;
    private final Long countOfKnownAccountTypes;
    private final String accountTypes;
    private final Set<String> webCategories = new HashSet<>();

    private final Content dataSource;
    private final long dataSourceId;

    /**
     * Create a ResultDomain from a String.
     *
     * @param domain The domain the result is being created from.
     */
    ResultDomain(String domain, Long activityStart, Long activityEnd, Long totalPageViews,
            Long pageViewsInLast60, Long filesDownloaded, Long countOfKnownAccountTypes, String accountTypes, Content dataSource) {
        this.domain = domain;
        this.dataSource = dataSource;
        this.dataSourceId = dataSource.getId();
        this.activityStart = activityStart;
        this.activityEnd = activityEnd;
        this.totalPageViews = totalPageViews;
        this.pageViewsInLast60 = pageViewsInLast60;
        this.filesDownloaded = filesDownloaded;
        this.countOfKnownAccountTypes = countOfKnownAccountTypes;
        this.accountTypes = accountTypes;
    }

    /**
     * Make a copy of the specified ResultDomain, without a category set.
     *
     * @param resultDomain The ResultDomain to copy
     */
    ResultDomain(ResultDomain resultDomain) {
        this.domain = resultDomain.getDomain();
        this.dataSource = resultDomain.getDataSource();
        this.dataSourceId = resultDomain.getDataSourceObjectId();
        this.activityStart = resultDomain.getActivityStart();
        this.activityEnd = resultDomain.getActivityEnd();
        this.totalPageViews = resultDomain.getTotalPageViews();
        this.pageViewsInLast60 = resultDomain.getPageViewsInLast60Days();
        this.filesDownloaded = resultDomain.getFilesDownloaded();
        this.countOfKnownAccountTypes = resultDomain.getCountOfKnownAccountTypes();
        this.accountTypes = resultDomain.getAccountTypes();
    }

    /**
     * Get the domain represented as a String.
     *
     * @return The String representation of the domain this result is for.
     */
    public String getDomain() {
        return this.domain;
    }

    /**
     * Get the date of first activity for this domain.
     *
     * @return The date of first activity for this domain.
     */
    public Long getActivityStart() {
        return activityStart;
    }

    /**
     * Get the date of most recent activity for this domain.
     *
     * @return The date of most recent activity for this domain.
     */
    public Long getActivityEnd() {
        return activityEnd;
    }

    /**
     * Get the total number of page views that this domain has had. Pages views
     * is defined as the count of TSK_WEB_HISTORY artifacts.
     *
     * @return The total number of page views that this domain has had.
     */
    public Long getTotalPageViews() {
        return totalPageViews;
    }

    /**
     * Get the number of page views that this domain has had in the last 60
     * days. Page views is defined as the count of TSK_WEB_HISTORY artifacts.
     *
     * @return The number of page views that this domain has had in the last 60
     *         days.
     */
    public Long getPageViewsInLast60Days() {
        return pageViewsInLast60;
    }

    /**
     * Get the number of files downloaded associated with this domain.
     *
     * @return The number of files downloaded associated with this domain.
     */
    public Long getFilesDownloaded() {
        return filesDownloaded;
    }

    /**
     * Get the web category (TSK_WEB_CATEGORY) type for this domain.
     */
    @NbBundle.Messages({
        "ResultDomain_getDefaultCategory=Uncategorized"
    })
    public Set<String> getWebCategories() {
        Set<String> returnList = new HashSet<>();
        if (webCategories.isEmpty()) {
            returnList.add(Bundle.ResultDomain_getDefaultCategory());
        } else {
            returnList.addAll(webCategories);
        }
        return returnList;
    }

    /**
     * Add the web categories for this domain (derived from TSK_WEB_CATEGORY)
     * artifacts.
     */
    public void addWebCategories(Set<String> categories) {
        if (categories != null && !categories.isEmpty()) {
            this.webCategories.addAll(categories);
        }
    }

    /**
     * Determines if the domain has been associated with a known account type
     * (TSK_WEB_ACCOUNT_TYPE).
     */
    public boolean hasKnownAccountType() {
        return getCountOfKnownAccountTypes() != null
                && getCountOfKnownAccountTypes() > 0;
    }

    /**
     * Get the account types which are associated with this domain.
     *
     * @return A comma seperated list of account types which are associated with
     *         this domain, or "Unknown" if no account types were associated
     *         with it.
     */
    @NbBundle.Messages({
        "ResultDomain_noAccountTypes=Unknown"
    })
    public String getAccountTypes() {
        if (StringUtils.isBlank(accountTypes)) {
            return Bundle.ResultDomain_noAccountTypes();
        }
        return accountTypes;
    }

    @Override
    public long getDataSourceObjectId() {
        return this.dataSourceId;
    }

    @Override
    public Content getDataSource() {
        return this.dataSource;
    }

    @Override
    public TskData.FileKnown getKnown() {
        return TskData.FileKnown.UNKNOWN;
    }

    @Override
    public SearchData.Type getType() {
        return SearchData.Type.DOMAIN;
    }

    @Override
    public String toString() {
        return "[domain=" + this.domain + ", data_source=" + this.dataSourceId + ", start="
                + this.activityStart + ", end=" + this.activityEnd + ", totalVisits=" + this.totalPageViews + ", visitsLast60="
                + this.pageViewsInLast60 + ", downloads=" + this.filesDownloaded + ", frequency="
                + this.getFrequency() + "]";
    }

    /**
     * @return the countOfKnownAccountTypes
     */
    Long getCountOfKnownAccountTypes() {
        return countOfKnownAccountTypes;
    }
}
