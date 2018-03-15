/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2018 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Support for TSK_EMAIL_MSG nodes and displaying emails in the directory tree.
 * Email messages are grouped into parent folders, and the folders are grouped
 * into parent accounts if TSK_PATH is available to define the relationship
 * structure for every message.
 */
public class EmailExtracted implements AutopsyVisitableItem {

    private static final String LABEL_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getDisplayName();
    private static final Logger logger = Logger.getLogger(EmailExtracted.class.getName());
    private static final String MAIL_ACCOUNT = NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.mailAccount.text");
    private static final String MAIL_FOLDER = NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.mailFolder.text");
    private static final String MAIL_PATH_SEPARATOR = "/";
    /**
     * Parse the path of the email msg to get the account name and folder in
     * which the email is contained.
     *
     * @param path - the TSK_PATH to the email msg
     *
     * @return a map containg the account and folder which the email is stored
     *         in
     */
    public static final Map<String, String> parsePath(String path) {
        Map<String, String> parsed = new HashMap<>();
        String[] split = path.split(MAIL_PATH_SEPARATOR);
        if (split.length < 4) {
            parsed.put(MAIL_ACCOUNT, NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultAcct.text"));
            parsed.put(MAIL_FOLDER, NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.defaultFolder.text"));
            return parsed;
        }
        parsed.put(MAIL_ACCOUNT, split[2]);
        parsed.put(MAIL_FOLDER, split[3]);
        return parsed;
    }
    private SleuthkitCase skCase;
    private final EmailResults emailResults;


    public EmailExtracted(SleuthkitCase skCase) {
        this.skCase = skCase;
        emailResults = new EmailResults();
    }


    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }
    private final class EmailResults extends Observable {
        
        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Map<String, List<Long>>> accounts = new LinkedHashMap<>();

        EmailResults() {
            update();
        }

        public Set<String> getAccounts() {
            synchronized (accounts) {
                return accounts.keySet();
            }
        }

        public Set<String> getFolders(String account) {
            synchronized (accounts) {
                return accounts.get(account).keySet();
            }
        }

        public List<Long> getArtifactIds(String account, String folder) {
            synchronized (accounts) {
                return accounts.get(account).get(folder);
            }
        }

        @SuppressWarnings("deprecation")
        public void update() {
            synchronized (accounts) {
                accounts.clear();
            }
            if (skCase == null) {
                return;
            }

            int artId = BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID();
            int pathAttrId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID();
            String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + pathAttrId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                synchronized (accounts) {
                    while (resultSet.next()) {
                        final String path = resultSet.getString("value_text"); //NON-NLS
                        final long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                        final Map<String, String> parsedPath = parsePath(path);
                        final String account = parsedPath.get(MAIL_ACCOUNT);
                        final String folder = parsedPath.get(MAIL_FOLDER);

                        Map<String, List<Long>> folders = accounts.get(account);
                        if (folders == null) {
                            folders = new LinkedHashMap<>();
                            accounts.put(account, folders);
                        }
                        List<Long> messages = folders.get(folder);
                        if (messages == null) {
                            messages = new ArrayList<>();
                            folders.put(folder, messages);
                        }
                        messages.add(artifactId);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "Cannot initialize email extraction: ", ex); //NON-NLS
            }
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Mail root node grouping all mail accounts, supports account-> folder
     * structure
     */
    public class RootNode extends DisplayableItemNode {

        public RootNode() {
            super(Children.create(new AccountFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(LABEL_NAME);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/mail-icon-16.png"); //NON-NLS
            emailResults.update();
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Mail root child node creating each account node
     */
    private class AccountFactory extends ChildFactory.Detachable<String> implements Observer {

        /*
         * The pcl is in the class because it has the easiest mechanisms to add
         * and remove itself during its life cycles.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) {
                            emailResults.update();
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getOpenCase();
                        emailResults.update();
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            emailResults.update();
            emailResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            emailResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(emailResults.getAccounts());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new AccountNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Account node representation
     */
    public class AccountNode extends DisplayableItemNode implements Observer {

        private final String accountName;

        public AccountNode(String accountName) {
            super(Children.create(new FolderFactory(accountName), true), Lookups.singleton(accountName));
            super.setName(accountName);
            this.accountName = accountName;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/account-icon-16.png"); //NON-NLS
            updateDisplayName();
            emailResults.addObserver(this);
        }

        private void updateDisplayName() {
            super.setDisplayName(accountName + " (" + emailResults.getFolders(accountName) + ")");
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Account node child creating sub nodes for every folder
     */
    private class FolderFactory extends ChildFactory<String> implements Observer {

        private final String accountName;

        private FolderFactory(String accountName) {
            super();
            this.accountName = accountName;
            emailResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(emailResults.getFolders(accountName));
            return true;
        }

        @Override
        protected Node createNodeForKey(String folderName) {
            return new FolderNode(accountName, folderName);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Node representing mail folder
     */
    public class FolderNode extends DisplayableItemNode implements Observer {

        private final String accountName;
        private final String folderName;

        public FolderNode(String accountName, String folderName) {
            super(Children.create(new MessageFactory(accountName, folderName), true), Lookups.singleton(accountName));
            super.setName(folderName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-16.png"); //NON-NLS
            this.accountName = accountName;
            this.folderName = folderName;
            updateDisplayName();
            emailResults.addObserver(this);
        }

        private void updateDisplayName() {
            super.setDisplayName(folderName + " (" + emailResults.getArtifactIds(accountName, folderName).size() + ")");

        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Node representing mail folder content (mail messages)
     */
    private class MessageFactory extends ChildFactory<Long> implements Observer {

        private final String accountName;
        private final String folderName;

        private MessageFactory(String accountName, String folderName) {
            super();
            this.accountName = accountName;
            this.folderName = folderName;
            emailResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            list.addAll(emailResults.getArtifactIds(accountName, folderName));
            return true;
        }

        @Override
        protected Node createNodeForKey(Long artifactId) {
            if (skCase == null) {
                return null;
            }
            try {
                BlackboardArtifact artifact = skCase.getBlackboardArtifact(artifactId);
                return new BlackboardArtifactNode(artifact);
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error creating mail messages nodes", ex); //NON-NLS
            }
            return null;
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
