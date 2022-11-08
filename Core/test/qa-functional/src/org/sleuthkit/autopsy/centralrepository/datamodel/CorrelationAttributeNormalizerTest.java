/*
 * 
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.util.Arrays;
import java.util.List;
import junit.framework.Test;
import org.junit.Assert;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;

/**
 * Tests for validation on each correlation attribute type.
 */
public class CorrelationAttributeNormalizerTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CorrelationAttributeNormalizerTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public CorrelationAttributeNormalizerTest(String name) {
        super(name);
    }

    public void testValidateMd5() {
        final String aValidHash = "e34a8899ef6468b74f8a1048419ccc8b";           //should pass
        final String anInValidHash = "e34asdfa8899ef6468b74f8a1048419ccc8b";    //should fail
        final String aValidHashWithCaps = "E34A8899EF6468B74F8A1048419CCC8B";   //should pass and be lowered
        final String emptyHash = "";                                            //should fail
        final String nullHash = null;                                             //should fail

        final int FILES_TYPE_ID = CorrelationAttributeInstance.FILES_TYPE_ID;

        try {
            assertTrue("This hash should just work", CorrelationAttributeNormalizer.normalize(FILES_TYPE_ID, aValidHash).equals(aValidHash));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This hash just needs to be converted to lower case", CorrelationAttributeNormalizer.normalize(CorrelationAttributeInstance.FILES_TYPE_ID, aValidHashWithCaps).equals(aValidHash));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CorrelationAttributeNormalizer.normalize(FILES_TYPE_ID, anInValidHash);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            CorrelationAttributeNormalizer.normalize(FILES_TYPE_ID, emptyHash);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            CorrelationAttributeNormalizer.normalize(FILES_TYPE_ID, nullHash);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
    }
    private static final String WE_EXPECT_AN_EXCEPTION_HERE = "We expect an exception here.";
    private static final String THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION = "This should have thrown an exception.";

    /**
     * Class for organizing records of successfully parsing and fail to parse
     * domains.
     */
    private static class DomainData {

        private final String originalString;
        private final String resultDomain;
        private final boolean shouldParse;

        static DomainData fail(String originalString) {
            return new DomainData(originalString, null, false);
        }

        static DomainData pass(String originalString, String resultDomain) {
            return new DomainData(originalString, resultDomain, true);
        }

        private DomainData(String originalString, String resultDomain, boolean shouldParse) {
            this.originalString = originalString;
            this.resultDomain = resultDomain;
            this.shouldParse = shouldParse;
        }

        String getOriginalString() {
            return originalString;
        }

        String getResultDomain() {
            return resultDomain;
        }

        boolean shouldParse() {
            return shouldParse;
        }
    }

    private static final String THIS_DOMAIN_SHOULD_PASS = "This domain should pass.";

    private static final List<DomainData> DOMAIN_DATA = Arrays.asList(
            DomainData.pass("www.test.com", "test.com"),
            DomainData.fail("http://www.test.com"),
            DomainData.pass("test.com", "test.com"),
            DomainData.fail("http://1270.0.1"),
            DomainData.fail("?>\\/)(*&.com"),
            DomainData.fail(null),
            DomainData.fail(""),
            DomainData.fail("HTTP://tests.com"),
            DomainData.fail("http://www.test.com/aPage?aQuestion=aParam&anotherQuestion=anotherParam"),
            DomainData.pass("WWW.TEST.COM", "test.com"),
            DomainData.pass("TEST.COM", "test.com")
    );

    public void testValidateDomain() {
        final int DOMAIN_TYPE_ID = CorrelationAttributeInstance.DOMAIN_TYPE_ID;
        for (DomainData item : DOMAIN_DATA) {
            if (item.shouldParse()) {
                String input = item.getOriginalString();
                String expected = item.getResultDomain();
                try {
                    String normalizedDomain = CorrelationAttributeNormalizer.normalize(DOMAIN_TYPE_ID, input);
                    assertTrue(String.format("Expected domain '%s' to be normalized, but was null.", item.getOriginalString()), normalizedDomain != null);
                    assertTrue(String.format("Was unable to normalize domain '%s' to '%s' but received %s instead.", input, expected, normalizedDomain), normalizedDomain.equals(expected));
                } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                    Exceptions.printStackTrace(ex);
                    fail(String.format("Unable to properly parse %s to %s.  Received: %s", input, expected, ex.getMessage()));
                }

            } else {
                try {
                    CorrelationAttributeNormalizer.normalize(DOMAIN_TYPE_ID, item.getOriginalString());
                    fail(String.format("Original string: '%s' should have failed to parse.", item.getOriginalString()));
                } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
                    assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
                }
            }
        }
    }

    public void testValidateEmail() {
        final String goodEmailOne = "bsweeney@cipehrtechsolutions.com";     //should pass
        final String goodEmailTwo = "BSWEENEY@ciphertechsolutions.com";     //should pass and be lowered
        final String badEmailThree = "";                                    //should fail
        final String badEmailFour = null;                                   //should fail
        final String badEmailFive = "asdf";                                 //should fail
        final String goodEmailSix = "asdf@asdf"; //TODO looks bad but the lib accepts it...
        final String badEmailSeven = "asdf.asdf";                           //should

        final int EMAIL_TYPE_ID = CorrelationAttributeInstance.EMAIL_TYPE_ID;

        try {
            assertTrue("This email should pass.", CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, goodEmailOne).equals(goodEmailOne));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This email should pass.", CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, goodEmailTwo).equals(goodEmailTwo.toLowerCase()));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, badEmailThree);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, badEmailFour);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, badEmailFive);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try { //TODO consider a better library?
            assertTrue("This email should pass", CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, goodEmailSix).equals(goodEmailSix));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            fail(ex.getMessage());
        }
        try {
            CorrelationAttributeNormalizer.normalize(EMAIL_TYPE_ID, badEmailSeven);
            fail(THIS_SHOULD_HAVE_THROWN_AN_EXCEPTION);
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
    }

    public void testValidatePhone() {
        final String goodPnOne = "19784740486";
        final String goodPnTwo = "1(978) 474-0486";
        final String goodPnThree = "+19784740486";
        final String goodPnFour = "1 978-474-0486";
        final String badPnFive = "9879879819784740486";
        final String goodPnSix = "+1(978) 474-0486";
        final String goodPnSeven = "+1(978) 474-0486";
        final String badPnEight = "asdfasdfasdf";
        final String badPnNine = "asdf19784740486adsf";

        final int PHONE_TYPE_ID = CorrelationAttributeInstance.PHONE_TYPE_ID;

        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnOne).equals(goodPnOne));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnTwo).equals(goodPnOne));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnThree).equals(goodPnThree));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnFour).equals(goodPnOne));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, badPnFive);
            //fail("This should have thrown an exception.");    //this will eventually pass when we do a better job at this
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnSix).equals(goodPnThree));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue(THIS_PHONE_NUMBER_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, goodPnSeven).equals(goodPnThree));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, badPnEight);
            fail("This should have thrown an exception.");
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
        try {
            CorrelationAttributeNormalizer.normalize(PHONE_TYPE_ID, badPnNine);
            fail("This should have thrown an exception.");
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            assertTrue(WE_EXPECT_AN_EXCEPTION_HERE, true);
        }
    }
    private static final String THIS_PHONE_NUMBER_SHOULD_PASS = "This phone number should pass.";

    public void testValidateUsbId() {
        //TODO will need to be updated once usb validation does something interesting
        final String goodIdOne = "0202:AAFF";       //should pass
        /*final String goodIdTwo = "0202:aaff";       //should pass
        final String badIdThree = "0202:axxf";      //should fail
        final String badIdFour = "";                //should fail
        final String badIdFive = null;              //should fail
        final String goodIdSix = "0202 AAFF";       //should pass
        final String goodIdSeven = "0202AAFF";      //should pass
        final String goodIdEight = "0202-AAFF";     //should pass*/

        final int USBID_TYPE_ID = CorrelationAttributeInstance.USBID_TYPE_ID;

        try {
            assertTrue(THIS_USB_ID_SHOULD_PASS, CorrelationAttributeNormalizer.normalize(USBID_TYPE_ID, goodIdOne).equals(goodIdOne));
        } catch (CentralRepoException | CorrelationAttributeNormalizationException ex) {
            Assert.fail(ex.getMessage());
        }
    }
    private static final String THIS_USB_ID_SHOULD_PASS = "This USB ID should pass.";
}
