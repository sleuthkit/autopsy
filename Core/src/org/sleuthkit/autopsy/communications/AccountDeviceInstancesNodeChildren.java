/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.util.Collections;
import java.util.List;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.AccountDeviceInstance;

class AccountDeviceInstancesNodeChildren extends Children.Keys<AccountDeviceInstance> {

    private final List<AccountDeviceInstance> accountDeviceInstances;

    AccountDeviceInstancesNodeChildren(List<AccountDeviceInstance> accountDeviceInstances) {
        super(true);
        this.accountDeviceInstances = accountDeviceInstances;
    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        setKeys(Collections.emptySet());
    }

    @Override
    protected void addNotify() {
        super.addNotify();
        setKeys(accountDeviceInstances);
    }

    //These are the methods for ChildFactory. I am going to keep them around but commented until we make a final descision.
    //    @Override
    //    protected boolean createKeys(List<Account> list) {
    //        list.addAll(accounts);
    //        return true;
    //    }
    //    
    //    @Override
    //    protected Node createNodeForKey(Account key) {
    //        return new AccountDeviceInstanceNode(key);
    //    }
    @Override
    protected Node[] createNodes(AccountDeviceInstance key) {
        return new Node[]{new AccountDeviceInstanceNode(key)};
    }
}
