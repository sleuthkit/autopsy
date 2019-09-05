/*
 * Autopsy Forensic Browser
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

//import java.io.IOException;
//import org.junit.After;
//import org.junit.AfterClass;
//import org.junit.Before;
//import org.junit.BeforeClass;
import org.junit.Test;
//import static org.junit.Assert.*;

/**
 * Tests for the BingTranslator translation service, these tests have been
 * commented out because they require credentials to perform
 */
public class BingTranslatorTest {

    @Test
    public void testTranslate() {
//        BingTranslator translator = new BingTranslator();
//        String input = "gato";
//        String expectedTranslation = "cat";
//        runTest(translator, input, expectedTranslation);
    }

//    @Test
//    public void testQuickStartSentence() throws Exception {
//        BingTranslator translator = new BingTranslator();
//        String input = "Willkommen bei Microsoft Translator. Raten Sie mal, wie viele Sprachen ich spreche.";
//        String expectedTranslation = "Welcome to Microsoft Translator. Guess how many languages I speak.";
//        runTest(translator, input, expectedTranslation);
//    }
//    
//    @Test
//    public void testCharacterEscapes() throws Exception {
//        BingTranslator translator = new BingTranslator();
//        String input = "\"gato\"";;
//        String expectedTranslation = "Cat";
//        runTest(translator, input, expectedTranslation);
//    }
//    
//    @Test
//    public void testLineBreaks() throws Exception {
//        BingTranslator translator = new BingTranslator();
//        String input = "gato\nperro";;
//        String expectedTranslation = "cat\nDog";
//        runTest(translator, input, expectedTranslation);
//    }
//    
//    /**
//     * Test whether translator throws an error. This should not be part of our
//     * regular testing, because we are limited to only 2MB of free translations
//     * ever.
//     * @param translator A BingTranslator
//     * @param input Text to translate
//     * @param expectedTranslation Not used unless you uncomment those lines.
//     */
//    public void runTest(BingTranslator translator, String input, String expectedTranslation) {
//        String translation;
//        try {
//            translation = translator.translate(input);
//        } 
//        catch (Throwable e) {
//            fail("Bing translation produced an exception: " + e.getMessage());
//            return;
//        };
//        
//        /*
//        //It's unrealistic to expect the same answer every time, but sometimes
//        //it's helpful to have this in your debug process.
//        assertEquals("Result did not match expected result", expectedTranslation, translation);
//        */
//    }
}
