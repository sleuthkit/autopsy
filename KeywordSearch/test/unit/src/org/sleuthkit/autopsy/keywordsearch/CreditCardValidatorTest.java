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

import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreditCardValidatorTest {

    public CreditCardValidatorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testLengthMatchesBin() {
        //amex must be 15
        assertEquals(true, CreditCardValidator.isValidCCN("3431 136294 58529"));
        assertEquals(false, CreditCardValidator.isValidCCN("3431-136294-5850")); //too short
        assertEquals(false, CreditCardValidator.isValidCCN("34311362945850"));// too short
        assertEquals(false, CreditCardValidator.isValidCCN("3431 1362 9458 5034")); //too long

        //verve 16 or 19
        assertEquals(false, CreditCardValidator.isValidCCN("506099002418342")); // 15 too short
        assertEquals(true, CreditCardValidator.isValidCCN("5060990024183426")); //16
        assertEquals(false, CreditCardValidator.isValidCCN("50609900241834267")); // 17 
        assertEquals(false, CreditCardValidator.isValidCCN("506099002418342675")); //18 
        assertEquals(true, CreditCardValidator.isValidCCN("5060990024183426759")); //19
        assertEquals(false, CreditCardValidator.isValidCCN("50609900241834267591")); //20

        //visa (theoretically 12-19 digits)
        assertEquals(false, CreditCardValidator.isValidCCN("4000 0000 000")); //11 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002")); //12 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 2")); //13 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 26")); //14 visa
        assertEquals(false, CreditCardValidator.isValidCCN("4000 0000 0002 267")); //15 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 000000 02267")); //15 visa  //is this right to enforce grouping?
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 2675")); //16 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 2675 9")); //17 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 2675 91")); //18 visa
        assertEquals(true, CreditCardValidator.isValidCCN("4000 0000 0002 2675 918")); //19 visa
        assertEquals(false, CreditCardValidator.isValidCCN("4000 0000 0002 2675 9183")); //20 visa

        //vs visa electron (only 16 digits (?))
        assertEquals(false, CreditCardValidator.isValidCCN("4026 1200 006")); //11 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065")); //12 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 9")); //13 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 91")); //14 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 918")); //15 visa electron
        assertEquals(true, CreditCardValidator.isValidCCN("4026 0000 0065 9187")); //16 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 9187 0")); //17 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 9183 00")); //18 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 9183 000")); //19 visa electron
        assertEquals(false, CreditCardValidator.isValidCCN("4026 0000 0065 9183 0000")); //20 visa electron
    }

    /**
     * Test of isValidCCN method, of class CreditCardValidator.
     */
    @Test
    public void testIsValidCCN16() {
        //rules for separators and grouping for 16 digits
        assertEquals(true, CreditCardValidator.isValidCCN("1234567890318342"));// dashes
        assertEquals(true, CreditCardValidator.isValidCCN("1234-5678-9031-8342"));// dashes
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031 8342"));// or spaces

        assertEquals(false, CreditCardValidator.isValidCCN("1234567890318341"));// luhn
        assertEquals(false, CreditCardValidator.isValidCCN("1234-5678-9031 8342")); //only one seperator
        assertEquals(false, CreditCardValidator.isValidCCN("1234-5678-90-318342")); //only four groups of four
        assertEquals(false, CreditCardValidator.isValidCCN("1234 5678 90 318342")); //only four groups of four
        assertEquals(false, CreditCardValidator.isValidCCN("1-2-3-4-5-6-7-8-9-0-3-1-8-3-4-2")); //only four groups of four
    }

    @Test
    public void testIsValidCCN15() {
        //amex are fifteen digits, and grouped 4 6 5
        //amex cards that strart with 34
        assertEquals(true, CreditCardValidator.isValidCCN("3431 136294 58529"));
        assertEquals(true, CreditCardValidator.isValidCCN("3431-136294-58529"));
        assertEquals(true, CreditCardValidator.isValidCCN("343113629458529"));

        assertEquals(false, CreditCardValidator.isValidCCN("343113629458528")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("3431 13629458 529")); //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("3431 136294-58529")); //separators

        //amex cards that start with 37
        assertEquals(true, CreditCardValidator.isValidCCN("377585291285489"));
        assertEquals(true, CreditCardValidator.isValidCCN("3775-852912-85489"));
        assertEquals(true, CreditCardValidator.isValidCCN("3775 852912 85489"));

        assertEquals(false, CreditCardValidator.isValidCCN("377585291285488")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("3775-852912 85489")); //separator
        assertEquals(false, CreditCardValidator.isValidCCN("37-7585-29-1285489")); //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("377585 29128548 9")); //grouping

        //UATP are also 15 digits, start with 1 and are typically 4-5-6
//        assertEquals(true,  CreditCardValidator.isValidCCN("1409 56201 545229"));
//        assertEquals(true,  CreditCardValidator.isValidCCN("1409-56201-545229"));
//        assertEquals(true,  CreditCardValidator.isValidCCN("140956201545229"));
//        assertEquals(false,  CreditCardValidator.isValidCCN("140 9562015 45229"));
//        assertEquals(false,  CreditCardValidator.isValidCCN("1409-56201 545229"));
    }

    @Test
    public void testIsValidCCN19() {
        //nineteen digit (visa) cards 4-4-4-4-3
        assertEquals(true, CreditCardValidator.isValidCCN("4539747947839518654"));
        assertEquals(true, CreditCardValidator.isValidCCN("4539-7479-4783-9518-654"));
        assertEquals(true, CreditCardValidator.isValidCCN("4539 7479 4783 9518 654"));

        assertEquals(false, CreditCardValidator.isValidCCN("4539747947839518653")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("4539-7479 4783 9518 654")); //separators
        assertEquals(false, CreditCardValidator.isValidCCN("45374 79 4783 9518 654")); //grouping

        //nineteen digit China UnionPay is 19 digits 6-13 (or 4-4-4-4) beging 62
        assertEquals(true, CreditCardValidator.isValidCCN("6239747947839518659"));
        assertEquals(true, CreditCardValidator.isValidCCN("623974 7947839518659"));
        assertEquals(true, CreditCardValidator.isValidCCN("623974-7947839518659"));
        /*
         * China UnionPay may not use luhn ??? *
         * https://stackoverflow.com/questions/7863058/does-the-luhn-algorithm-work-for-all-mainstream-credit-cards-discover-visa-m
         */
        assertEquals(false, CreditCardValidator.isValidCCN("6239747947839518658")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("623974-79478395 18659")); //separators
        assertEquals(false, CreditCardValidator.isValidCCN("62397-47947839518659")); //grouping
    }

    @Test
    public void testIsValidCCN18() {
        assertEquals(true, CreditCardValidator.isValidCCN("123456789031834267"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031 8342 67"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234-56789031834-267"));

        assertEquals(false, CreditCardValidator.isValidCCN("123456789031834266")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("123 456789031834267")); //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("1234-56789 031834267")); //separators
    }

    @Test
    public void testIsValidCCN17() {
        assertEquals(true, CreditCardValidator.isValidCCN("12345678903183426"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031 8342 6"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234-56789031834-26"));

        assertEquals(false, CreditCardValidator.isValidCCN("12345678903183425"));//luhn
        assertEquals(false, CreditCardValidator.isValidCCN("123 45678903183426")); //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("1234-56789 03183426")); //separators
    }

    @Test
    public void testIsValidCCN14() {
        assertEquals(true, CreditCardValidator.isValidCCN("12345678903183"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031 83"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234-5678903183"));

        assertEquals(false, CreditCardValidator.isValidCCN("12345678903182"));//luhn
        assertEquals(false, CreditCardValidator.isValidCCN("123 45678903183")); //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("1234-56789 03183")); //separators
    }

    @Test
    public void testIsValidCCN13() {
        assertEquals(true, CreditCardValidator.isValidCCN("1234567890318"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031 8"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234-567890318"));

        assertEquals(false, CreditCardValidator.isValidCCN("1234567890317"));//luhn
        assertEquals(false, CreditCardValidator.isValidCCN("123 4567890318")); //grouping 
        assertEquals(false, CreditCardValidator.isValidCCN("1234-56789 0318")); //separators
    }

    @Test
    public void testIsValidCCN12() {
        assertEquals(true, CreditCardValidator.isValidCCN("123456789031"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234 5678 9031"));
        assertEquals(true, CreditCardValidator.isValidCCN("1234-56789031"));

        assertEquals(false, CreditCardValidator.isValidCCN("123456789030")); //luhn
        assertEquals(false, CreditCardValidator.isValidCCN("123 456789031"));  //grouping
        assertEquals(false, CreditCardValidator.isValidCCN("1234-56789 031")); //separators
    }

}
