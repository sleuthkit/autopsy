/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications.relationships;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * ChildFactory for CorrelationCases. Finds the cases that reference the given
 * list of accounts.
 */
final class CorrelationCaseChildNodeFactory extends ChildFactory<CorrelationCase> {

    private static final Logger logger = Logger.getLogger(CorrelationCaseChildNodeFactory.class.getName());

    private final Set<Account> accounts;

    /**
     * ChildFactory for CorrelationCases.
     *
     * @param accounts List of Account objects
     */
    CorrelationCaseChildNodeFactory(Set<Account> accounts) {
        this.accounts = accounts;
    }

    @Override
    protected boolean createKeys(List<CorrelationCase> list) {
        if (!CentralRepository.isEnabled()) {
            return true;
        }

        CentralRepository dbInstance;
        try {
            dbInstance = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Unable to connect to the Central Repository database.", ex); //NON-NLS
            return false;
        }

        Map<String, CorrelationCase> uniqueCaseMap = new HashMap<>();
        
        String currentCaseName = Case.getCurrentCase().getName();

        accounts.forEach((account) -> {
            try {
                Optional<CorrelationAttributeInstance.Type> optCorrelationType = getCorrelationType(account.getAccountType());
                if (optCorrelationType.isPresent()) {
                    List<CorrelationAttributeInstance> correlationInstances = dbInstance.getArtifactInstancesByTypeValue(optCorrelationType.get(), account.getTypeSpecificID());
                    correlationInstances.forEach((correlationInstance) -> {
                        CorrelationCase correlationCase = correlationInstance.getCorrelationCase();
                        if(!correlationCase.getCaseUUID().equals(currentCaseName)) {
                            uniqueCaseMap.put(correlationCase.getCaseUUID(), correlationCase);
                        }
                    });
                }
            } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                logger.log(Level.WARNING, String.format("Unable to getArtifactInstance for accountID: %d", account.getAccountID()), ex); //NON-NLS
            }
        });

        list.addAll(uniqueCaseMap.values());

        return true;
    }

    @Override
    protected Node createNodeForKey(CorrelationCase correlationCase) {
        return new CaseNode(correlationCase);
    }

    /**
     * Find the CorrelationAttributeInstance.Type for the given Account.Type.
     *
     * @param accountType Account type
     *
     * @return CorrelationAttributeInstance.Type for given account or empty if
     *         there is no match
     *
     * @throws CentralRepoException
     */
    private Optional<CorrelationAttributeInstance.Type> getCorrelationType(Account.Type accountType) throws CentralRepoException { 

        String accountTypeStr = accountType.getTypeName();
        if (Account.Type.DEVICE.getTypeName().equalsIgnoreCase(accountTypeStr) == false) {
            Optional<CentralRepoAccount.CentralRepoAccountType> optCrAccountType = CentralRepository.getInstance().getAccountTypeByName(accountTypeStr);
            if (optCrAccountType.isPresent()) {
                int corrTypeId = optCrAccountType.get().getCorrelationTypeId();
                return Optional.of(CentralRepository.getInstance().getCorrelationTypeById(corrTypeId));
            }
        }
        return Optional.empty();
    }

    /**
     * Simple AbstractNode for a CorrelationCase. The property sheet only
     * contains the creation date.
     */
    final class CaseNode extends AbstractNode {

        private final CorrelationCase correlationCase;

        /**
         * Construct the object, set the display name and icon.
         *
         * @param correlationCase
         */
        CaseNode(CorrelationCase correlationCase) {
            super(Children.LEAF);
            this.correlationCase = correlationCase;

            setDisplayName(correlationCase.getDisplayName());
            setIconBaseWithExtension("org/sleuthkit/autopsy/images/briefcase.png"); //NON-NLS
        }

        @Override
        protected Sheet createSheet() {
            super.createSheet();
            Sheet sheet = new Sheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>("creationDate", //NON-NLS
                    correlationCase.getTitleCreationDate(),
                    correlationCase.getTitleCreationDate(),
                    correlationCase.getCreationDate()));

            return sheet;
        }
    }

}
