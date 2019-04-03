/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;
import java.util.Collection;
import org.sleuthkit.datamodel.CommunicationsFilter;

/**
 * Provide the singleton EventBus.
 */
final class CVTEvents {

    private final static EventBus cvtEventBus = new EventBus();

    static EventBus getCVTEventBus() {
        return cvtEventBus;
    }

    private CVTEvents() {
    }

    static final class FilterChangeEvent {

        private final CommunicationsFilter newFilter;

        CommunicationsFilter getNewFilter() {
            return newFilter;
        }

        FilterChangeEvent(CommunicationsFilter newFilter) {
            this.newFilter = newFilter;
        }

    }

    static final class PinAccountsEvent {

        private final ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances;
        private final boolean replace;

        public boolean isReplace() {
            return replace;
        }

        ImmutableSet<AccountDeviceInstanceKey> getAccountDeviceInstances() {
            return accountDeviceInstances;
        }

        PinAccountsEvent(Collection<? extends AccountDeviceInstanceKey> accountDeviceInstances, boolean replace) {
            this.accountDeviceInstances = ImmutableSet.copyOf(accountDeviceInstances);
            this.replace = replace;
        }
    }

    static final class UnpinAccountsEvent {

        private final ImmutableSet<AccountDeviceInstanceKey> accountDeviceInstances;

        public ImmutableSet<AccountDeviceInstanceKey> getAccountDeviceInstances() {
            return accountDeviceInstances;
        }

         UnpinAccountsEvent(Collection<? extends AccountDeviceInstanceKey> accountDeviceInstances) {
            this.accountDeviceInstances = ImmutableSet.copyOf(accountDeviceInstances);
        }
    }
}
