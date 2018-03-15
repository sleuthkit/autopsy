/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

/**
 * Extension of ProxyLookup that exposes the ability to change the Lookups
 * delegated to.
 *
 */
final class ModifiableProxyLookup extends ProxyLookup {

    ModifiableProxyLookup(final Lookup... lookups) {
        super(lookups);
    }

    /**
     * Set the Lookups delegated to by this lookup.
     *
     * @param lookups The new Lookups to delegate to.
     */
    void setNewLookups(final Lookup... lookups) {
        /* default */
        setLookups(lookups);
    }
}
