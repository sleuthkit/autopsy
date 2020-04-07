/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors;

import junit.framework.Assert;
import org.junit.Test;


/**
 * Tests methods present in the TextFileExtractor
 */
public class TextFileExtractorTest {

    @Test
    public void testIsSupported() {
        Assert.assertFalse(new TextFileExtractor(null).isSupported());
    }
    
    
}
