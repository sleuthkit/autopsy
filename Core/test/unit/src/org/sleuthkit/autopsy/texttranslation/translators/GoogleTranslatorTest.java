/*
 * Autopsy
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
package org.sleuthkit.autopsy.texttranslation.translators;

import org.junit.Test;

/**
 * Tests for the GoogleTranslator, tests have been commented out due to necessary credentials file for tests to succeed 
 */
public class GoogleTranslatorTest {

    /**
     * Test of translate method, of class GoogleTranslator.
     */
    @Test
    public void testTranslate() throws Exception {
//        String string = "traducar";
//        GoogleTranslator instance = new GoogleTranslator();
//        String result = instance.translate(string);
//
//        //It's unrealistic to expect the same answer every time, but sometimes
//        //it's helpful to have this in your debug process.
//        
//        String expResult = "translate"; assertEquals(expResult, result);
//        assertEquals("Result did not match expected result" expResult, result);
         
    }
//Commented out because using TranslateOption with the current version of Guava is not supported JIRA-5063
//    @Test
//    public void testQuickStartInstructions() throws Exception {
//        //This was the code from Google's tutorial.
//        
//        // Instantiates a client
//        Translate translate = TranslateOptions.getDefaultInstance().getService();
//
//        // The text to translate
//        String text = "Hello, world!";
//
//        // Translates some text
//        Translation translation =
//                translate.translate(
//                        text,
//                        Translate.TranslateOption.sourceLanguage("en"),
//                        Translate.TranslateOption.targetLanguage("es"));
//        String result = translation.getTranslatedText();
//        
//        //It's unrealistic to expect the same answer every time, but sometimes
//        //it's helpful to have this in your debug process.
//        String expResult = "¡Hola Mundo!";
//        assertEquals("Result did not match expected result", expResult, result);
//    }
}
