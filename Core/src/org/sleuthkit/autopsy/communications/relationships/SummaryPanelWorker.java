/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.centralrepository.datamodel.PersonaAccount;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * Runnable SwingWorker for gather the data that the Summary panel needs.
 */
class SummaryPanelWorker extends SwingWorker<SummaryPanelWorker.SummaryWorkerResults, Void> {

    private final static Logger logger = Logger.getLogger(SummaryPanelWorker.class.getName());
    
    private final Account account;
    private final SelectionInfo selectionInfo;

    // Construct a instance
    SummaryPanelWorker(SelectionInfo selectionInfo, Account account) {
        this.account = account;
        this.selectionInfo = selectionInfo;
    }

    /**
     * Returns the account the worker is gathering data for.
     *
     * @return
     */
    Account getAccount() {
        return account;
    }

    @Override
    protected SummaryWorkerResults doInBackground() throws Exception {
        CentralRepoAccount crAccount = null;
        List<String> stringList = new ArrayList<>();
        List<AccountFileInstance> accountFileInstanceList = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager().getAccountFileInstances(account);
        if (accountFileInstanceList != null) {
            for (AccountFileInstance instance : accountFileInstanceList) {
                stringList.add(instance.getFile().getUniquePath());
            }
        }

        List<Persona> personaList = new ArrayList<>();
        if (CentralRepository.isEnabled()) {
            Collection<PersonaAccount> personaAccountList = PersonaAccount.getPersonaAccountsForAccount(account);
            PersonaAccount.getPersonaAccountsForAccount(account);

            for (PersonaAccount pAccount : personaAccountList) {
                personaList.add(pAccount.getPersona());
            }

            Optional<CentralRepoAccount.CentralRepoAccountType> optCrAccountType = CentralRepository.getInstance().getAccountTypeByName(account.getAccountType().getTypeName());
            if (optCrAccountType.isPresent()) {
                try {
                    crAccount = CentralRepository.getInstance().getAccount(optCrAccountType.get(), account.getTypeSpecificID());
                } catch (InvalidAccountIDException unused) {
                    // This was probably caused to a phone number not making
                    // threw the normalization.
                    logger.log(Level.WARNING, String.format("Exception thrown from CR getAccount for account %s (%d)", account.getTypeSpecificID(), account.getAccountID()));
                }
            }
        }
        
        return new SummaryWorkerResults(stringList, personaList, crAccount, new AccountSummary(account, selectionInfo.getArtifacts()));
    }

    /**
     * Wraps the results of the worker for easy of returning and usage by the
     * SummaryViewer.
     */
    final static class SummaryWorkerResults {

        private final List<String> accountFileInstancePaths;
        private final List<Persona> personaList;
        private final CentralRepoAccount centralRepoAccount;
        private final AccountSummary accountSummary;

        /**
         * Constructor.
         *
         * @param accountFileInstancePaths List of instance paths.
         * @param personaList              List of personas for the account
         * @param centralRepoAccount       CentralRepoAccount for the given
         *                                 account, maybe null if CR is not
         *                                 enabled.
         */
        SummaryWorkerResults(List<String> accountFileInstancePaths, List<Persona> personaList, CentralRepoAccount centralRepoAccount, AccountSummary accountSummary) {
            this.accountFileInstancePaths = accountFileInstancePaths;
            this.personaList = personaList;
            this.centralRepoAccount = centralRepoAccount;
            this.accountSummary = accountSummary;
        }

        /**
         * Returns the list of instance paths for the account given to the
         * worker.
         *
         * @return
         */
        List<String> getPaths() {
            return accountFileInstancePaths;
        }

        /**
         * Returns the list of personas found for the account given to the
         * worker. This list maybe empty if none were found or cr is not
         * enabled.
         *
         * @return
         */
        List<Persona> getPersonaList() {
            return personaList;
        }

        /**
         * Return the cr account for the account given to the worker. This maybe
         * null if the cr was not enabled.
         *
         * @return
         */
        CentralRepoAccount getCRAccount() {
            return centralRepoAccount;
        }
        
        AccountSummary getAccountSummary() {
            return accountSummary;
        }
    }

}
