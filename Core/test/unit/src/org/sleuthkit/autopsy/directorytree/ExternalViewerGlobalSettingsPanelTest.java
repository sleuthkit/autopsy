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
 * 72% code coverage of ExternalViewerGlobalSettingsPanel
 */
public class ExternalViewerGlobalSettingsPanelTest {
    
    static final String[] testColumnNames = {"A", "B"};
    
    /**
     * Default constructor for JUnit
     */
    public ExternalViewerGlobalSettingsPanelTest(){
        //Codacy complains if there is no comment here
    }
    
    @Test
    public void testEnableButtons() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerGlobalSettingsPanel panel = new ExternalViewerGlobalSettingsPanel(testModel);
        Assert.assertFalse(panel.enableButtons());
        
        testModel.addRule(new ExternalViewerRule("image/png", "fake.exe", RuleType.MIME));
        
        Assert.assertFalse(panel.enableButtons());
        panel.setSelectionInterval(0, 0);
        Assert.assertTrue(panel.enableButtons());
    }
    
    @Test
    public void testDisableButtons() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerGlobalSettingsPanel panel = new ExternalViewerGlobalSettingsPanel(testModel);
        
        testModel.addRule(new ExternalViewerRule("image/png", "fake.exe", RuleType.MIME));
        Assert.assertFalse(panel.enableButtons());
        panel.setSelectionInterval(0, 0);
        Assert.assertTrue(panel.enableButtons());
        
        testModel.removeRule(0);
        Assert.assertFalse(panel.enableButtons());
    }

    @Test
    public void testDeleteRuleButtonClick() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerGlobalSettingsPanel testPanel = new ExternalViewerGlobalSettingsPanel(testModel);
        Assert.assertFalse(testPanel.enableButtons());
        
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testPanel.setSelectionInterval(0, 0);
        Assert.assertTrue(testPanel.enableButtons());
        Assert.assertEquals(1, testModel.getRowCount());
        
        testPanel.deleteRuleButtonClick(0);
        
        Assert.assertFalse(testPanel.enableButtons());
        Assert.assertEquals(0, testModel.getRowCount());
    }
    
    @Test(expected = IndexOutOfBoundsException.class)
    public void testDeleteButtonClickFail() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerGlobalSettingsPanel testPanel = new ExternalViewerGlobalSettingsPanel(testModel);
        
        testPanel.deleteRuleButtonClick(-1);
    }
    
    @Test
    public void testSingleSelection() {
        ExternalViewerGlobalSettingsTableModel testModel = new ExternalViewerGlobalSettingsTableModel(testColumnNames);
        ExternalViewerGlobalSettingsPanel testPanel = new ExternalViewerGlobalSettingsPanel(testModel);
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        testModel.addRule(new ExternalViewerRule(".txt", "notepad.exe", RuleType.EXT));
        
        testPanel.setSelectionInterval(0, 2);
        
        Assert.assertFalse(testPanel.isSelected(0));
        Assert.assertFalse(testPanel.isSelected(1));
        Assert.assertTrue(testPanel.isSelected(2));
    }
}
