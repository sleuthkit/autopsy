/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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

import java.lang.reflect.InvocationTargetException;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;

/**
 * A subclass of NodeProperty that stores an account object for use with looking
 * up personas.
 */
class AccountNodeProperty<T> extends NodeProperty<T> {

    private final Account account;

    AccountNodeProperty(String name, String displayName, T value, Account account) {
        super(name, displayName, "", value);
        this.account = account;
    }

    @Override
    public String getShortDescription() {
        try {
            if (account != null) {
                return RelationshipsNodeUtilities.getAccoutToolTipText(getValue().toString(), account);
            }
            return getValue().toString();
        } catch (IllegalAccessException | InvocationTargetException ex) {
            Exceptions.printStackTrace(ex);
        }

        return "";
    }
}
