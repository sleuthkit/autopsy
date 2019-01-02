/*
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
package org.sleuthkit.autopsy.directorytree;

import junit.framework.Assert;
import org.junit.Test;
import org.sleuthkit.autopsy.directorytree.ExternalViewerRule.RuleType;

/**
 * 100% code coverage of ExternalViewerGlobalSettingsTableModel
 */
public class ExternalViewerGlobalSettingsTableModelTest {
    
    static final String[] testColumnNames = {"A", "B"};
    
    public ExternalViewerGlobalSettingsTableModelTest() {
    }

    /**
     * Test of addRule method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testAddRule() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        
        Assert.assertEquals(1, testModel.getRowCount());
        
        ExternalViewerRule rule = testModel.getRuleAt(0);
        Assert.assertEquals("image/png", rule.getName());
        Assert.assertEquals("test.exe", rule.getExePath());
        Assert.assertEquals(RuleType.MIME, rule.getRuleType());
    }

    /**
     * Test of getRowCount method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetRowCount() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        Assert.assertEquals(0, testModel.getRowCount());
        
        testModel.addRule(new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".wav", "video.exe", RuleType.EXT));
        
        Assert.assertEquals(3, testModel.getRowCount());
    }

    /**
     * Test of getColumnName method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetColumnName() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        Assert.assertEquals("A", testModel.getColumnName(0));
        Assert.assertEquals("B", testModel.getColumnName(1));
    }
    
    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testColumnNameOutOfBounds() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.getColumnName(2);
    }

    /**
     * Test of getColumnClass method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetColumnClass() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        Assert.assertEquals(String.class, testModel.getColumnClass(0));
    }

    /**
     * Test of getColumnCount method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetColumnCount() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        Assert.assertEquals(2, testModel.getColumnCount());
        ExternalViewerGlobalSettingsTableModel testModelTwo = new ExternalViewerGlobalSettingsTableModel(new String[] {"A", "B", "C", "D", "E"});
        Assert.assertEquals(5, testModelTwo.getColumnCount());
    }

    /**
     * Test of getValueAt method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetValueAt() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".wav", "video.exe", RuleType.EXT));
        
        Assert.assertEquals(".txt", testModel.getValueAt(1,0));
        Assert.assertEquals("notepad.exe", testModel.getValueAt(1,1));
        Assert.assertEquals("image/png", testModel.getValueAt(0,0));
        Assert.assertEquals("test.exe", testModel.getValueAt(0,1));
    }

    /**
     * Test of getRuleAt method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testGetRuleAt() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".wav", "video.exe", RuleType.EXT));
        
        ExternalViewerRule rule = testModel.getRuleAt(1);
        Assert.assertEquals(".txt", rule.getName());
        Assert.assertEquals("notepad.exe", rule.getExePath());
        Assert.assertEquals(RuleType.EXT, rule.getRuleType());
        
        ExternalViewerRule ruleTwo = testModel.getRuleAt(0);
        Assert.assertEquals("image/png", ruleTwo.getName());
        Assert.assertEquals("test.exe", ruleTwo.getExePath());
        Assert.assertEquals(RuleType.MIME, ruleTwo.getRuleType());
    }

    /**
     * Test of setRule method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testSetRule() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".wav", "video.exe", RuleType.EXT));
        
        testModel.setRule(0, new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        ExternalViewerRule rule = testModel.getRuleAt(1);
        Assert.assertEquals(".txt", rule.getName());
        Assert.assertEquals("notepad.exe", rule.getExePath());
        Assert.assertEquals(RuleType.EXT, rule.getRuleType());
        
        testModel.setRule(2, new ExternalViewerRule("image/png", "test.exe", RuleType.MIME));
        ExternalViewerRule ruleTwo = testModel.getRuleAt(2);
        Assert.assertEquals("image/png", ruleTwo.getName());
        Assert.assertEquals("test.exe", ruleTwo.getExePath());
        Assert.assertEquals(RuleType.MIME, ruleTwo.getRuleType());
    }

    /**
     * Test of removeRule method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testRemoveRule() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerRule rule = new ExternalViewerRule("image/png", "test.ext", RuleType.MIME);
        testModel.addRule(rule);
        Assert.assertEquals(1, testModel.getRowCount());
        
        testModel.removeRule(0);
        Assert.assertEquals(0, testModel.getRowCount());
        Assert.assertFalse(testModel.containsRule(rule));
    }

    /**
     * Test of isCellEditable method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testIsCellEditable() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        Assert.assertFalse(testModel.isCellEditable(0, 0));
    }

    /**
     * Test of containsRule method, of class ExternalViewerGlobalSettingsTableModel.
     */
    @Test
    public void testContainsRule() {
        ExternalViewerRule rule = new ExternalViewerRule("image/png", "test.exe", RuleType.MIME);
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(rule);
        Assert.assertTrue(testModel.containsRule(rule));
    }
    
    @Test
    public void testNotContains() {
        ExternalViewerRule rule = new ExternalViewerRule("image/png", "test.exe", RuleType.MIME);
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        testModel.addRule(rule);
        Assert.assertFalse(testModel.containsRule(new ExternalViewerRule("not", "a rule", RuleType.EXT)));
        Assert.assertFalse(testModel.containsRule(null));
    }
}