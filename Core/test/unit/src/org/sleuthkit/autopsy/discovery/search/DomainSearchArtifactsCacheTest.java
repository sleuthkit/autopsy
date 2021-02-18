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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class DomainSearchArtifactsCacheTest {

    private static final ARTIFACT_TYPE WEB_ARTIFACT_TYPE = ARTIFACT_TYPE.TSK_WEB_BOOKMARK;
    private static final BlackboardAttribute.Type TSK_DOMAIN = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN);
    private static final BlackboardAttribute.Type TSK_URL = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL);

    @Test(expected = IllegalArgumentException.class)
    public void get_NonWebArtifactType_ShouldThrow() throws DiscoveryException {
        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(null, "google.com", ARTIFACT_TYPE.TSK_CALLLOG);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        cache.get(request);
    }

    /*
     * This test is important for ensuring artifact loading can
     * be cancelled, which is necessary for a responsive UI.
     */
    @Test
    public void get_ThreadInterrupted_ShouldThrow() throws TskCoreException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "facebook.com", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        Thread.currentThread().interrupt();
        try {
            cache.get(request);
            // Clear the interrupt flag on failure.
            Thread.interrupted();
            Assert.fail("Should have thrown a discovery exception.");
        } catch (DiscoveryException ex) {
            // Clear the interrupt flag on success (or failure).
            Thread.interrupted();
            Assert.assertEquals(InterruptedException.class, ex.getCause().getClass());
        }
    }

    @Test
    public void get_MatchingDomain_ShouldHaveSizeOne() throws TskCoreException, DiscoveryException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockArtifact.getAttribute(TSK_DOMAIN)).thenReturn(mockDomainAttribute("google.com"));
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "google.com", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        List<BlackboardArtifact> artifacts = cache.get(request);
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals(mockArtifact, artifacts.get(0));
    }

    @Test
    public void get_MismatchedDomainName_ShouldBeEmpty() throws TskCoreException, DiscoveryException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockArtifact.getAttribute(TSK_DOMAIN)).thenReturn(mockDomainAttribute("google.com"));
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "facebook.com", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        List<BlackboardArtifact> artifacts = cache.get(request);
        Assert.assertEquals(0, artifacts.size());
    }

    @Test
    public void get_MismatchedUrl_ShouldBeEmpty() throws DiscoveryException, TskCoreException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockArtifact.getAttribute(TSK_URL)).thenReturn(mockURLAttribute("https://www.dce1.com/search"));
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "dce.com", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        List<BlackboardArtifact> artifacts = cache.get(request);
        Assert.assertEquals(0, artifacts.size());
    }

    @Test
    public void get_CaseInsensitiveDomainAttribute_ShouldHaveSizeOne() throws TskCoreException, DiscoveryException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockArtifact.getAttribute(TSK_DOMAIN)).thenReturn(mockDomainAttribute("xYZ.coM"));
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "xyz.com", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        List<BlackboardArtifact> artifacts = cache.get(request);
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals(mockArtifact, artifacts.get(0));
    }

    @Test
    public void get_CaseInsensitiveRequestDomain_ShouldHaveSizeOne() throws TskCoreException, DiscoveryException {
        SleuthkitCase mockCase = mock(SleuthkitCase.class);
        BlackboardArtifact mockArtifact = mock(BlackboardArtifact.class);
        when(mockArtifact.getAttribute(TSK_DOMAIN)).thenReturn(mockDomainAttribute("google.com"));
        when(mockCase.getBlackboardArtifacts(WEB_ARTIFACT_TYPE)).thenReturn(Lists.newArrayList(mockArtifact));

        DomainSearchArtifactsRequest request = new DomainSearchArtifactsRequest(mockCase, "GooGle.coM", WEB_ARTIFACT_TYPE);
        DomainSearchArtifactsCache cache = new DomainSearchArtifactsCache();
        List<BlackboardArtifact> artifacts = cache.get(request);
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals(mockArtifact, artifacts.get(0));
    }

    private BlackboardAttribute mockDomainAttribute(String value) {
        return new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DOMAIN, "", value);
    }

    private BlackboardAttribute mockURLAttribute(String value) {
        return new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, "", value);
    }
}
