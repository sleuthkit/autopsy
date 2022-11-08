/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_EMAIL_MSG;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datamodel.Artifacts.UpdatableCountTypeNode;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * Support for TSK_EMAIL_MSG nodes and displaying emails in the directory tree.
 * Email messages are grouped into parent folders, and the folders are grouped
 * into parent accounts if TSK_PATH is available to define the relationship
 * structure for every message.
 */
public class EmailExtracted implements AutopsyVisitableItem {

    private static final String LABEL_NAME = BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeName();
    private static final Logger logger = Logger.getLogger(EmailExtracted.class.getName());
    private static final String MAIL_ACCOUNT = NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.mailAccount.text");
    private static final String MAIL_FOLDER = NbBundle.getMessage(EmailExtracted.class, "EmailExtracted.mailFolder.text");
    private static final String MAIL_PATH_SEPARATOR = "/";
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED);

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
        String[] split = path == null ? new String[0] : path.split(MAIL_PATH_SEPARATOR);
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
    private final long filteringDSObjId;    // 0 if not filtering/grouping by data source

    /**
     * Constructor
     *
     * @param skCase Case DB
     */
    public EmailExtracted(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    /**
     * Constructor
     *
     * @param skCase Case DB
     * @param objId  Object id of the data source
     *
     */
    public EmailExtracted(SleuthkitCase skCase, long objId) {
        this.skCase = skCase;
        this.filteringDSObjId = objId;
        emailResults = new EmailResults();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
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
            // clear cache if no case
            if (skCase == null) {
                synchronized (accounts) {
                    accounts.clear();
                }
                return;
            }

            // get artifact id and path (if present) of all email artifacts
            int emailArtifactId = BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID();
            int pathAttrId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID();

            String query = "SELECT \n"
                    + "	art.artifact_obj_id AS artifact_obj_id,\n"
                    + "	(SELECT value_text FROM blackboard_attributes attr\n"
                    + "	WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = " + pathAttrId + "\n"
                    + "	LIMIT 1) AS value_text\n"
                    + "FROM \n"
                    + "	blackboard_artifacts art\n"
                    + "	WHERE art.artifact_type_id = " + emailArtifactId + "\n"
                    + ((filteringDSObjId > 0) ? "	AND art.data_source_obj_id = " + filteringDSObjId : "");

            // form hierarchy of account -> folder -> account id
            Map<String, Map<String, List<Long>>> newMapping = new HashMap<>();

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    Long artifactObjId = resultSet.getLong("artifact_obj_id");
                    Map<String, String> accountFolderMap = parsePath(resultSet.getString("value_text"));
                    String account = accountFolderMap.get(MAIL_ACCOUNT);
                    String folder = accountFolderMap.get(MAIL_FOLDER);

                    Map<String, List<Long>> folders = newMapping.computeIfAbsent(account, (str) -> new LinkedHashMap<>());
                    List<Long> messages = folders.computeIfAbsent(folder, (str) -> new ArrayList<>());
                    messages.add(artifactObjId);
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "Cannot initialize email extraction: ", ex); //NON-NLS
            }

            synchronized (accounts) {
                accounts.clear();
                accounts.putAll(newMapping);
            }

            setChanged();
            notifyObservers();
        }
    }

    /**
     * Mail root node grouping all mail accounts, supports account-> folder
     * structure
     */
    public class RootNode extends UpdatableCountTypeNode {

        public RootNode() {
            super(Children.create(new AccountFactory(), true),
                    Lookups.singleton(TSK_EMAIL_MSG.getDisplayName()),
                    TSK_EMAIL_MSG.getDisplayName(),
                    filteringDSObjId,
                    TSK_EMAIL_MSG);
            //super(Children.create(new AccountFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(LABEL_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/mail-icon-16.png"); //NON-NLS
            emailResults.update();
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return sheet;
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
                        Case.getCurrentCaseThrows();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_EMAIL_MSG.getTypeID()) {
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
                        Case.getCurrentCaseThrows();
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

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            emailResults.update();
            emailResults.addObserver(this);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
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
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
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
     * Ensures that the key for the parent node and child factory is the same to
     * ensure that the BaseChildFactory registered listener node name
     * (BaseChildFactory.register and DataResultViewerTable.setNode with event
     * registration) is the same as the factory name that will post events from
     * BaseChildFactory.post called in BaseChildFactory.makeKeys. See JIRA-7752
     * for more details.
     *
     * @param accountName The account name.
     * @param folderName  The folder name.
     *
     * @return The generated key.
     */
    private static String getFolderKey(String accountName, String folderName) {
        return accountName + "_" + folderName;
    }

    /**
     * Node representing mail folder
     */
    public class FolderNode extends DisplayableItemNode implements Observer {

        private final String accountName;
        private final String folderName;

        public FolderNode(String accountName, String folderName) {
            super(Children.create(new MessageFactory(accountName, folderName), true), Lookups.singleton(accountName));
            super.setName(getFolderKey(accountName, folderName));
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
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "EmailExtracted.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
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
    private class MessageFactory extends BaseChildFactory<DataArtifact> implements Observer {

        private final String accountName;
        private final String folderName;

        private MessageFactory(String accountName, String folderName) {
            super(getFolderKey(accountName, folderName));
            this.accountName = accountName;
            this.folderName = folderName;
            emailResults.addObserver(this);
        }

        @Override
        protected Node createNodeForKey(DataArtifact art) {
            return new BlackboardArtifactNode(art);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected List<DataArtifact> makeKeys() {
            List<DataArtifact> keys = new ArrayList<>();

            if (skCase != null) {
                emailResults.getArtifactIds(accountName, folderName).forEach((id) -> {
                    try {
                        DataArtifact art = skCase.getBlackboard().getDataArtifactById(id);
                        //Cache attributes while we are off the EDT.
                        //See JIRA-5969
                        art.getAttributes();
                        keys.add(art);
                    } catch (TskCoreException ex) {
                        logger.log(Level.WARNING, "Error getting mail messages keys", ex); //NON-NLS
                    }
                });
            }
            return keys;
        }

        @Override
        protected void onAdd() {
            // No-op
        }

        @Override
        protected void onRemove() {
            // No-op
        }
    }
}
