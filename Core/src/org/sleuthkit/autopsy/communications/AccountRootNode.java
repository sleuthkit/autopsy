/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.Account;

public class AccountRootNode extends AbstractNode {

    public AccountRootNode(List<Account> accounts) {

        super(Children.create(new AccountsNodeFactory(accounts), true));
    }

    private static class AccountsNodeFactory extends ChildFactory<Account> {

        private final List<  Account> accounts;

        private AccountsNodeFactory(List<Account> accounts) {
            this.accounts = accounts;
        }

        @Override
        protected boolean createKeys(List<Account> list) {
            list.addAll(accounts);
            return true;
        }

        @Override
        protected Node createNodeForKey(Account key) {
            return new AccountNode(accounts);
        }

    }
}
