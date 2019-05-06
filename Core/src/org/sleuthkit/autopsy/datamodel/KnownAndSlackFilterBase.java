/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.util.function.Predicate;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskData;

/**
 * Predicate that can be used to filter known and/or slack files from
 * Content collections based on user preferences.
 */
abstract class KnownAndSlackFilterBase<T extends Content> implements Predicate<T> {
    protected static boolean filterKnown;
    protected static boolean filterSlack;

    @Override
    public boolean test(T t) {
        AbstractFile af = null;

        if (t instanceof AbstractFile) {
            af = (AbstractFile) t;
        }

        if (af != null) {
            if (af.getKnown() == TskData.FileKnown.KNOWN && filterKnown) {
                return false;
            }
            if (af.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) && filterSlack) {
                return false;
            }
        }

        return true;
    }
}
