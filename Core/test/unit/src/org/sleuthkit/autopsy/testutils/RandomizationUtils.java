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
package org.sleuthkit.autopsy.testutils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tools for pseudo-randomization.
 */
public final class RandomizationUtils {

    /**
     * Returns list in 0, n-1, 1, n-2 ... order. Deterministic so same results
     * each time, but not in original order.
     *
     * @return Mixed up list.
     */
    public static <T> List<T> getMixedUp(List<T> list) {
        int forward = 0;
        int backward = list.size() - 1;

        List<T> newList = new ArrayList<>();
        while (forward <= backward) {
            newList.add(list.get(forward));

            if (forward < backward) {
                newList.add(list.get(backward));
            }

            forward++;
            backward--;
        }

        return newList;
    }

    private RandomizationUtils() {
    }
}
