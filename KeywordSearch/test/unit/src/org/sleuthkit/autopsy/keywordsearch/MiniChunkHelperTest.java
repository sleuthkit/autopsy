/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * tests for MiniChunkHelper
 */
public class MiniChunkHelperTest {

    @Test
    public void isMiniChunkID() {
        assertTrue(MiniChunkHelper.isMiniChunkID("1_1_mini"));
        assertFalse(MiniChunkHelper.isMiniChunkID("1_1"));
        assertFalse(MiniChunkHelper.isMiniChunkID("1"));
    }

    @Test
    public void getBaseChunkID() {
        Assert.assertEquals("1_1", MiniChunkHelper.getBaseChunkID("1_1_mini"));
        Assert.assertEquals("1_1", MiniChunkHelper.getBaseChunkID("1_1"));
        Assert.assertEquals("1", MiniChunkHelper.getBaseChunkID("1"));
    }

}