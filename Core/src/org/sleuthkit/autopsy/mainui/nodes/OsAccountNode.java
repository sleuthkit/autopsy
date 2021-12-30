/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.datamodel.TskContentItem;
import org.sleuthkit.autopsy.mainui.datamodel.ContentRowDTO.OsAccountRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.OsAccountsDAO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import static org.sleuthkit.autopsy.mainui.nodes.BaseNode.backgroundTasksPool;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.autopsy.mainui.sco.SCOUtils;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node representing a row for an OsAccount in the results table.
 */
public class OsAccountNode extends BaseNode<SearchResultsDTO, OsAccountRowDTO> implements SCOSupporter {

    private static final Logger logger = Logger.getLogger(OsAccountNode.class.getName());
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/os-account.png";
    
    private FutureTask<String> realmFutureTask = null;

    public OsAccountNode(SearchResultsDTO results, OsAccountRowDTO rowData) {
        super(Children.LEAF,
                Lookups.fixed(rowData.getContent(), new TskContentItem<>(rowData.getContent())),
                results,
                rowData);
        String name = rowData.getContent().getName();
        setName(ContentNodeUtil.getContentName(rowData.getContent().getId()));
        setDisplayName(name);
        setShortDescription(name);
        setIconBaseWithExtension(ICON_PATH);
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }
    
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        updateRealmColumns();
        return sheet;
    }    

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }
    
    /**
     * Start a background task that will update the host name, realm name, and realm scope columns when complete.
     */
    private void updateRealmColumns() {
        if (realmFutureTask != null && !realmFutureTask.isDone()) {
            realmFutureTask.cancel(true);
            realmFutureTask = null;
        }

        if ((realmFutureTask == null || realmFutureTask.isDone())) {
            realmFutureTask = new FutureTask<>(new RealmFetcher<>(new WeakReference<>(this)), "");
            backgroundTasksPool.submit(realmFutureTask);
        }
    }
    @Override
    public Optional<Content> getContent() {
        return Optional.ofNullable(getRowDTO().getContent());
    }

    @Override
    public Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        return SCOUtils.getCountPropertyAndDescription(attribute, defaultDescription);
    }

    @Override
    public DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        return SCOUtils.getCommentProperty(tags, attributes);
    }
    
    static class RealmFetcher<T extends Content> implements Runnable {
    
        private final WeakReference<OsAccountNode> weakSupporterRef;
        private static final Logger logger = Logger.getLogger(RealmFetcher.class.getName());

        /**
         * Construct a new RealmFetcher.
         *
         * @param weakSupporterRef A weak reference to a SCOSupporter.
         */
        RealmFetcher(WeakReference<OsAccountNode> weakSupporterRef) {
            this.weakSupporterRef = weakSupporterRef;
        }
    
        @Override
        public void run() {
            try {
                RealmData data = doInBackground();

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        RealmFetcher.done(data, weakSupporterRef.get());
                    }
                });

            } catch (Exception ex) {
                logger.log(Level.SEVERE, "An exception occurred while trying to update the the SCO data", ex);
            }
        }

        private RealmData doInBackground() throws Exception {
            OsAccountNode osAccountNode = weakSupporterRef.get();
            Optional<Content> content = osAccountNode.getContent();
            if (! content.isPresent() || ! (content.get() instanceof OsAccount)) {
                return null;
            }
            OsAccount osAcct = (OsAccount) content.get();

            try {
                SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                long realmId = osAcct.getRealmId();
                OsAccountRealm realm = skCase.getOsAccountRealmManager().getRealmByRealmId(realmId);
                List<Host> hosts = skCase.getOsAccountManager().getHosts(osAcct);
                return new RealmData(hosts, realm);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error loading host/realm information for OsAccount " + osAcct.getName() + "(ID: " + osAcct.getId() + ")", ex);
                return null;
            } catch (NoCurrentCaseException ex) {
                // Case is closing
                return null;
            }
        }
    
        @NbBundle.Messages({
            "RealmFetcher_nodescription_text=No description"
        })
        private static void done(RealmData data, OsAccountNode osAccountNode) {
            if (data == null || osAccountNode == null) {
                return;
            }
        
            List<NodeProperty<?>> props = new ArrayList<>();
            
            List<Host> hosts = data.getHosts();
            if (!hosts.isEmpty()) {
                String hostsString = hosts.stream()
                        .map(h -> h.getName().trim())
                        .distinct()
                        .sorted((a, b) -> a.compareToIgnoreCase(b))
                        .collect(Collectors.joining(", "));
                
                props.add(new NodeProperty<>(
                        OsAccountsDAO.HOST_COLUMN_NAME,
                        OsAccountsDAO.HOST_COLUMN_NAME,
                        Bundle.RealmFetcher_nodescription_text(),
                        hostsString));
            }
        
            String scopeName = data.getRealm().getScope().getName();
            if (StringUtils.isNotBlank(scopeName)) {
                props.add(new NodeProperty<>(
                        OsAccountsDAO.SCOPE_COLUMN_NAME,
                        OsAccountsDAO.SCOPE_COLUMN_NAME,
                        Bundle.RealmFetcher_nodescription_text(),
                        scopeName));
            }
        
            List<String> realmNames = data.getRealm().getRealmNames();
            if (!realmNames.isEmpty()) {
                String realmNamesStr = realmNames.stream()
                        .map(String::trim)
                        .distinct()
                        .sorted((a, b) -> a.compareToIgnoreCase(b))
                        .collect(Collectors.joining(", "));
                        
                props.add(new NodeProperty<>(
                        OsAccountsDAO.REALM_COLUMN_NAME,
                        OsAccountsDAO.REALM_COLUMN_NAME,
                        Bundle.RealmFetcher_nodescription_text(),
                        realmNamesStr));
            }
        
            if (!props.isEmpty()) {
                osAccountNode.updateSheet(props);
            }
        }
    }

    /**
     * Class for passing the realm data.
     */
    private static class RealmData {
        
        private final List<Host> hosts;
        private final OsAccountRealm realm;

        /**
         * Construct a new RealmData object.
         *
         * @param scoreAndDescription
         * @param comment
         * @param countAndDescription
         */
        RealmData(List<Host> hosts, OsAccountRealm realm) {
            this.hosts = hosts;
            this.realm = realm;
        }
        
        List<Host> getHosts() {
            return hosts;
        }
        
        OsAccountRealm getRealm() {
            return realm;
        }
    } 
}

