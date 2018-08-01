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

import junit.framework.Test;
import org.netbeans.junit.NbModuleSuite;
import org.netbeans.junit.NbTestCase;
import org.openide.util.Exceptions;

/**
 *
 * @author bsweeney
 */
public class CentralRepoIONormalizerTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoIONormalizerTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public CentralRepoIONormalizerTest(String name) {
        super(name);
    }

    public void testNormalizeMd5() {
        final String aValidHash = "e34a8899ef6468b74f8a1048419ccc8b";           //should pass
        final String anInValidHash = "e34asdfa8899ef6468b74f8a1048419ccc8b";    //should failo
        final String aValidHashWithCaps = "E34A8899EF6468B74F8A1048419CCC8B";   //should pass
        final String emptyHash = "";                                            //should fail
        final String nullHash = "";                                             //should fail

        final int FILES_TYPE_ID = CorrelationAttribute.FILES_TYPE_ID;

        try {
            assertTrue("This hash should just work", CentralRepoIONormalizer.normalize(FILES_TYPE_ID, aValidHash).equals(aValidHash));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This hash just needs to be converted to lower case", CentralRepoIONormalizer.normalize(CorrelationAttribute.FILES_TYPE_ID, aValidHashWithCaps).equals(aValidHash));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoIONormalizer.normalize(FILES_TYPE_ID, anInValidHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(FILES_TYPE_ID, emptyHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(FILES_TYPE_ID, nullHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
    }

    public void testNormalizeDomain() {
        final String goodDomainOne = "www.test.com";
        final String goodDomainTwo = "http://www.test.com";
        final String goodDomainThree = "test.com";
        final String goodDomainFour = "http://1270.0.1";
        final String badDomainFive = "?>\\/)(*&.com";
        final String badDomainSix = null;
        final String badDomainSeven = "";
        final String goodDomainEight = "HTTP://tests.com";

        final int DOMAIN_TYPE_ID = CorrelationAttribute.DOMAIN_TYPE_ID;

        try {
            assertTrue("This domain should pass.", CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, goodDomainOne).equals(goodDomainOne));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This domain should pass.", CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, goodDomainTwo).equals(goodDomainTwo));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This domain should pass.", CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, goodDomainThree).equals(goodDomainThree));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This domain should pass.", CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, goodDomainFour).equals(goodDomainFour));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, badDomainFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, badDomainSix);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, badDomainSeven);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            assertTrue("This domain should pass.", CentralRepoIONormalizer.normalize(DOMAIN_TYPE_ID, goodDomainEight).equals(goodDomainEight.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
    }

    public void testNormalizeEmail() {
        final String goodEmailOne = "bsweeney@cipehrtechsolutions.com";
        final String goodEmailTwo = "BSWEENEY@ciphertechsolutions.com";
        final String badEmailThree = "";
        final String badEmailFour = null;
        final String badEmailFive = "asdf";
        final String badEmailSix = "asdf@asdf";
        final String badEmailSeven = "asdf.asdf";

        final int EMAIL_TYPE_ID = CorrelationAttribute.EMAIL_TYPE_ID;

        try {
            assertTrue("This email should pass.", CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, goodEmailOne).equals(goodEmailOne));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This email should pass.", CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, goodEmailTwo).equals(goodEmailTwo.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, badEmailThree);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, badEmailFour);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, badEmailFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, badEmailSix);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(EMAIL_TYPE_ID, badEmailSeven);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
    }

    public void testNormalizePhone() {
        assertTrue("We haven't acutally tested anything here - TODO.", true);
    }

    public void testNormalizeUsbId() {
        final String goodIdOne = "0202:AAFF";
        final String goodIdTwo = "0202:aaff";
        final String goodIdThree = "0202:axxf";
        final String badIdFour = "";
        final String badIdFive = null;
        final String goodIdSix = "0202 AAFF";
        final String goodIdSeven = "0202AAFF";
        final String goodIdEight = "0202-AAFF";
                
        final int USBID_TYPE_ID = CorrelationAttribute.USBID_TYPE_ID;
        
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdOne).equals(goodIdOne));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdTwo).equals(goodIdTwo));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdThree).equals(goodIdThree));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoIONormalizer.normalize(USBID_TYPE_ID, badIdFour);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(USBID_TYPE_ID, badIdFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoIONormalizer.normalize(USBID_TYPE_ID, badIdFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdSix).equals(goodIdSix));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdSeven).equals(goodIdSeven));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoIONormalizer.normalize(USBID_TYPE_ID, goodIdEight).equals(goodIdEight));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }        
    }
}
