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
package org.sleuthkit.autopsy.timeline.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import static org.apache.commons.lang3.StringUtils.isBlank;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 */
public class TimelineDBUtils {

    private final SleuthkitCase sleuthkitCase;

    public TimelineDBUtils(SleuthkitCase sleuthkitCase) {
        this.sleuthkitCase = sleuthkitCase;
    }

    public String csvAggFunction(String args) {
        return (sleuthkitCase.getDatabaseType() == TskData.DbType.POSTGRESQL ? "string_agg" : "group_concat")
               + "(Cast (" + args + " AS VARCHAR) , '" + "," + "')";
    }

    /**
     * take the result of a group_concat SQLite operation and split it into a
     * set of X using the mapper to to convert from string to X If groupConcat
     * is empty, null, or all whitespace, returns an empty list.
     *
     * @param <X>         the type of elements to return
     * @param groupConcat a string containing the group_concat result ( a comma
     *                    separated list)
     * @param mapper      a function from String to X
     *
     * @return a Set of X, each element mapped from one element of the original
     *         comma delimited string
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public static <X> List<X> unGroupConcat(String groupConcat, CheckedFunction<String, X, TskCoreException> mapper) throws TskCoreException {

        if (isBlank(groupConcat)) {
            return Collections.emptyList();
        } 

        List<X> result = new ArrayList<>();
        for (String s : groupConcat.split(",")) {
            result.add(mapper.apply(s));
        }
        return result;
    }
}
