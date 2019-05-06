/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * ChildFactory that creates ContentNode representing the files that reference
 * the given list of accounts.
 */
final class AccountSourceContentChildNodeFactory extends ChildFactory<Content> {

    private static final Logger logger = Logger.getLogger(AccountSourceContentChildNodeFactory.class.getName());

    private final Set<Account> accounts;

    AccountSourceContentChildNodeFactory(Set<Account> accounts) {
        this.accounts = accounts;
    }

    @Override
    protected boolean createKeys(List<Content> list) {
        if (accounts == null || accounts.isEmpty()) {
            return true;
        }

        CommunicationsManager communicationManager;
        try {
            communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get communications manager from case.", ex); //NON-NLS
            return false;
        }

        accounts.forEach((account) -> {
            try {
                List<AccountFileInstance> accountFileInstanceList = communicationManager.getAccountFileInstances(account);

                for (AccountFileInstance fileInstance : accountFileInstanceList) {
                    list.add(fileInstance.getFile());
                }

            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Failed to getAccountFileInstances for account: %d", account.getAccountID()), ex); //NON-NLS
            }
        });

        return true;
    }

    @Override
    protected Node createNodeForKey(Content content) {
        return new ContentNode(content);
    }

    /**
     * Simple AbstractNode for a Content (file) object.
     */
    final class ContentNode extends AbstractNode {

        private final Content content;

        ContentNode(Content content) {
            super(Children.LEAF);
            this.content = content;
            
            try {
                setDisplayName(content.getUniquePath());
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, String.format("Unable to getUniquePath for Content: %d", content.getId()), ex); //NON-NLS
                setDisplayName(content.getName());
            }
            
            setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png");
        }
    }

}
