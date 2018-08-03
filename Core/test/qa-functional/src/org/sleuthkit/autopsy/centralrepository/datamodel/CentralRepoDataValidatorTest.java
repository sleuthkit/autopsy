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
 * Tests for validation on each correlation attribute type.
 */
public class CentralRepoDataValidatorTest extends NbTestCase {

    public static Test suite() {
        NbModuleSuite.Configuration conf = NbModuleSuite.createConfiguration(CentralRepoDataValidatorTest.class).
                clusters(".*").
                enableModules(".*");
        return conf.suite();
    }

    public CentralRepoDataValidatorTest(String name) {
        super(name);
    }

    public void testValidateMd5() {
        final String aValidHash = "e34a8899ef6468b74f8a1048419ccc8b";           //should pass
        final String anInValidHash = "e34asdfa8899ef6468b74f8a1048419ccc8b";    //should fail
        final String aValidHashWithCaps = "E34A8899EF6468B74F8A1048419CCC8B";   //should pass and be lowered
        final String emptyHash = "";                                            //should fail
        final String nullHash = null;                                             //should fail

        final int FILES_TYPE_ID = CorrelationAttribute.FILES_TYPE_ID;

        try {
            assertTrue("This hash should just work", CentralRepoDataValidator.validate(FILES_TYPE_ID, aValidHash).equals(aValidHash));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This hash just needs to be converted to lower case", CentralRepoDataValidator.validate(CorrelationAttribute.FILES_TYPE_ID, aValidHashWithCaps).equals(aValidHash));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoDataValidator.validate(FILES_TYPE_ID, anInValidHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(FILES_TYPE_ID, emptyHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(FILES_TYPE_ID, nullHash);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
    }

    public void testValidateDomain() {
        final String goodDomainOne = "www.test.com";            //should pass
        final String badDomainTwo = "http://www.test.com";      //should fail (includes protocol)
        final String goodDomainThree = "test.com";              //should pass
        final String badDomainFour = "http://1270.0.1";         //should fail
        final String badDomainFive = "?>\\/)(*&.com";           //should fail
        final String badDomainSix = null;                       //should fail
        final String badDomainSeven = "";                       //should fail
        final String badDomainEight = "HTTP://tests.com";       //should fail    
        final String badDomainNine = "http://www.test.com/aPage?aQuestion=aParam&anotherQuestion=anotherParam";     //should fail
        final String goodDomainTen = "WWW.TEST.COM";            //should pass but be lowered
        final String goodDomainEleven = "TEST.COM";             //should pass but be lowered

        final int DOMAIN_TYPE_ID = CorrelationAttribute.DOMAIN_TYPE_ID;

        try {
            assertTrue("This domain should pass.", CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, goodDomainOne).equals(goodDomainOne));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainTwo);
            fail("This should have thrown an exception");
        } catch (CentralRepoValidationException ex) {
            assertTrue("we expect an exception here.", true);
        }
        try {
            assertTrue("This domain should pass.", CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, goodDomainThree).equals(goodDomainThree));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This domain should pass.", CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainFour).equals(badDomainFour));
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainSix);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainSeven);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainEight);
            fail("This should have thrown an exception");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, badDomainNine);
            fail("This should have thrown an exception");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            assertTrue("This domain should pass.", CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, goodDomainTen).equals(goodDomainTen.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This domain should pass.", CentralRepoDataValidator.validate(DOMAIN_TYPE_ID, goodDomainEleven).equals(goodDomainEleven.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
    }

    public void testValidateEmail() {
        final String goodEmailOne = "bsweeney@cipehrtechsolutions.com";     //should pass
        final String goodEmailTwo = "BSWEENEY@ciphertechsolutions.com";     //should pass and be lowered
        final String badEmailThree = "";                                    //should fail
        final String badEmailFour = null;                                   //should fail
        final String badEmailFive = "asdf";                                 //should fail
        final String badEmailSix = "asdf@asdf"; //TODO looks bad but the lib accepts it...
        final String badEmailSeven = "asdf.asdf";                           //should

        final int EMAIL_TYPE_ID = CorrelationAttribute.EMAIL_TYPE_ID;

        try {
            assertTrue("This email should pass.", CentralRepoDataValidator.validate(EMAIL_TYPE_ID, goodEmailOne).equals(goodEmailOne));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This email should pass.", CentralRepoDataValidator.validate(EMAIL_TYPE_ID, goodEmailTwo).equals(goodEmailTwo.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            CentralRepoDataValidator.validate(EMAIL_TYPE_ID, badEmailThree);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(EMAIL_TYPE_ID, badEmailFour);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(EMAIL_TYPE_ID, badEmailFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
//        try { //TODO consider a better library?
//            CentralRepoDataValidator.validate(EMAIL_TYPE_ID, badEmailSix);
//            fail("This should have thrown an exception.");    //TODO do we need a better library?
//        } catch (CentralRepoValidationException ex) {
//            assertTrue("We expect an exception here.", true);
//        }
        try {
            CentralRepoDataValidator.validate(EMAIL_TYPE_ID, badEmailSeven);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
    }

    public void testValidatePhone() {
        assertTrue("We haven't acutally tested anything here - TODO.", true);
    }

    public void testValidateUsbId() {
        final String goodIdOne = "0202:AAFF";       //should pass and be lowered
        final String goodIdTwo = "0202:aaff";       //should pass
        final String badIdThree = "0202:axxf";      //should fail
        final String badIdFour = "";                //should fail
        final String badIdFive = null;              //should fail
        final String goodIdSix = "0202 AAFF";       //should pass
        final String goodIdSeven = "0202AAFF";      //should pass
        final String goodIdEight = "0202-AAFF";     //should pass
                
        final int USBID_TYPE_ID = CorrelationAttribute.USBID_TYPE_ID;
        
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, goodIdOne).equals(goodIdOne.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, goodIdTwo).equals(goodIdTwo));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, badIdThree).equals(badIdThree));
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);            
        }
        try {
            CentralRepoDataValidator.validate(USBID_TYPE_ID, badIdFour);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(USBID_TYPE_ID, badIdFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            CentralRepoDataValidator.validate(USBID_TYPE_ID, badIdFive);
            fail("This should have thrown an exception.");
        } catch (CentralRepoValidationException ex) {
            assertTrue("We expect an exception here.", true);
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, goodIdSix).equals(goodIdSix.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, goodIdSeven).equals(goodIdSeven.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }
        try {
            assertTrue("This USB ID should pass.", CentralRepoDataValidator.validate(USBID_TYPE_ID, goodIdEight).equals(goodIdEight.toLowerCase()));
        } catch (CentralRepoValidationException ex) {
            Exceptions.printStackTrace(ex);
            fail(ex.getMessage());
        }        
    }
}
