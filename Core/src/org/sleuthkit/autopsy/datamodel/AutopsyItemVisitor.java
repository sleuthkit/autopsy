/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.sleuthkit.autopsy.datamodel.accounts.Accounts;

/**
 * This visitor goes over the AutopsyVisitableItems, which are currently the
 * nodes in the tree that are structural and not nodes that are from
 * Sleuthkit-based data model objects.
 */
public interface AutopsyItemVisitor<T> {

    T visit(DataSources i);

    T visit(DataSourceGrouping datasourceGrouping);

    T visit(Views v);

    T visit(FileTypesByExtension sf);

    T visit(FileTypesByExtension.RootFilter fsf);

    T visit(FileTypesByExtension.DocumentFilter df);

    T visit(FileTypesByExtension.ExecutableFilter ef);

    T visit(RecentFiles rf);

    T visit(RecentFiles.RecentFilesFilter rff);

    T visit(DeletedContent dc);

    T visit(DeletedContent.DeletedContentFilter dcf);

    T visit(FileSize fs);

    T visit(FileSize.FileSizeFilter fsf);

    T visit(KeywordHits kh);

    T visit(HashsetHits hh);

    T visit(EmailExtracted ee);

    T visit(InterestingHits ih);

    T visit(Tags tagsNodeKey);

    T visit(Reports reportsItem);

    T visit(Accounts accountsItem);

    T visit(FileTypes fileTypesItem);

    T visit(FileTypesByMimeType aThis);
    
    T visit(OsAccounts osAccoutItem);

    T visit(HostGrouping aThis);

    T visit(PersonGrouping aThis);

    T visit(HostDataSources aThis);

    T visit(DataSourcesByType aThis);

    T visit(AnalysisResults aThis);

    T visit(DataArtifacts aThis);
    

    static abstract public class Default<T> implements AutopsyItemVisitor<T> {

        protected abstract T defaultVisit(AutopsyVisitableItem ec);

        @Override
        public T visit(FileTypesByExtension sf) {
            return defaultVisit(sf);
        }

        @Override
        public T visit(FileTypesByExtension.RootFilter fsf) {
            return defaultVisit(fsf);
        }

        @Override
        public T visit(FileTypesByExtension.DocumentFilter df) {
            return defaultVisit(df);
        }

        @Override
        public T visit(FileTypesByExtension.ExecutableFilter ef) {
            return defaultVisit(ef);
        }

        @Override
        public T visit(FileTypesByMimeType ftByMimeType) {
            return defaultVisit(ftByMimeType);
        }

        @Override
        public T visit(DeletedContent dc) {
            return defaultVisit(dc);
        }

        @Override
        public T visit(DeletedContent.DeletedContentFilter dcf) {
            return defaultVisit(dcf);
        }

        @Override
        public T visit(FileSize fs) {
            return defaultVisit(fs);
        }

        @Override
        public T visit(FileSize.FileSizeFilter fsf) {
            return defaultVisit(fsf);
        }

        @Override
        public T visit(RecentFiles rf) {
            return defaultVisit(rf);
        }

        @Override
        public T visit(RecentFiles.RecentFilesFilter rff) {
            return defaultVisit(rff);
        }

        @Override
        public T visit(KeywordHits kh) {
            return defaultVisit(kh);
        }

        @Override
        public T visit(HashsetHits hh) {
            return defaultVisit(hh);
        }

        @Override
        public T visit(InterestingHits ih) {
            return defaultVisit(ih);
        }

        @Override
        public T visit(EmailExtracted ee) {
            return defaultVisit(ee);
        }

        @Override
        public T visit(Tags tagsNodeKey) {
            return defaultVisit(tagsNodeKey);
        }

        @Override
        public T visit(DataSources i) {
            return defaultVisit(i);
        }

        @Override
        public T visit(Views v) {
            return defaultVisit(v);
        }

        @Override
        public T visit(DataSourceGrouping datasourceGrouping) {
            return defaultVisit(datasourceGrouping);
        }

        @Override
        public T visit(HostGrouping hostGrouping) {
            return defaultVisit(hostGrouping);
        }

        @Override
        public T visit(PersonGrouping personGrouping) {
            return defaultVisit(personGrouping);
        }

        @Override
        public T visit(FileTypes ft) {
            return defaultVisit(ft);
        }

        @Override
        public T visit(Reports reportsItem) {
            return defaultVisit(reportsItem);
        }

        @Override
        public T visit(Accounts accountsItem) {
            return defaultVisit(accountsItem);
        }

        @Override
        public T visit(OsAccounts osAccountItem) {
            return defaultVisit(osAccountItem);
        }

        @Override
        public T visit(HostDataSources hostDataSources) {
            return defaultVisit(hostDataSources);
        }
        
        @Override
        public T visit(DataSourcesByType dataSourceHosts) {
            return defaultVisit(dataSourceHosts);
        }
        
        @Override
        public T visit(DataArtifacts aThis) {
            return defaultVisit(aThis);
        }

        @Override
        public T visit(AnalysisResults aThis) {
            return defaultVisit(aThis);
        }
    }
}
