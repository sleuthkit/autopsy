/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils.esedb;

import java.time.Instant;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of the DateTime column decoding.
 */
public class EseDateTimeTest {

    @Test
    public void oleDateIsPreferred() {
        // 44927.5 days after 1899-12-30 = 2023-01-01 12:00 UTC.
        Optional<Instant> decoded = EseDateTime.decode(Double.doubleToLongBits(44927.5));
        Assert.assertEquals(Optional.of(Instant.parse("2023-01-01T12:00:00Z")), decoded);
    }

    @Test
    public void fileTimeFallback() {
        // 132565120200137766 = 2021-01-30 20:27:00.0137766 UTC.
        Optional<Instant> decoded = EseDateTime.decode(132565120200137766L);
        Assert.assertTrue(decoded.isPresent());
        Assert.assertEquals(Instant.parse("2021-01-30T20:27:00.013776600Z"), decoded.get());
    }

    @Test
    public void zeroAndImplausibleValuesAreEmpty() {
        Assert.assertFalse(EseDateTime.decode(0).isPresent());
        Assert.assertFalse(EseDateTime.decode(1).isPresent());
        Assert.assertFalse(EseDateTime.fromOleDate(Double.NaN).isPresent());
        Assert.assertFalse(EseDateTime.fromFileTime(-5).isPresent());
    }
}
