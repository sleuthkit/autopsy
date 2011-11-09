/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.awt.event.ActionEvent;
import javax.swing.JMenuItem;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecentCasesTest {
    RecentCases instance;

    public RecentCasesTest() {
        instance = RecentCases.getInstance();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        instance.actionPerformed(null);
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of getInstance method, of class RecentCases.
     */
    @Test
    public void testGetInstance() {
        System.out.println("getInstance");
        RecentCases expResult = RecentCases.getInstance();
        RecentCases result = RecentCases.getInstance();
        assertEquals(expResult, result);
        assertNotNull(result);
    }

    /**
     * Test of getMenuPresenter method, of class RecentCases.
     */
    @Test
    public void testGetMenuPresenter() {
        System.out.println("getMenuPresenter");
        JMenuItem result = instance.getMenuPresenter();
        assertNotNull(result);
    }

    /**
     * Test of actionPerformed method, of class RecentCases.
     */
    @Test
    public void testActionPerformed() {
        System.out.println("actionPerformed");
        ActionEvent e = null;
        instance.addRecentCase("test", "test");
        instance.actionPerformed(e);
        assertEquals(instance.getTotalRecentCases(), 0);

    }

    /**
     * Test of addRecentCase method, of class RecentCases.
     */
    @Test
    public void testAddRecentCase() {
        System.out.println("addRecentCase");
        String name = "name";
        String path = "C:\\path";
        instance.addRecentCase(name, path);
        instance.addRecentCase(name, path);
        assertEquals(name, instance.getRecentCaseNames()[0]);
        assertEquals(path, instance.getRecentCasePaths()[0]);
        assertEquals(1, instance.getTotalRecentCases());
    }

    /**
     * Test of updateRecentCase method, of class RecentCases.
     */
    @Test
    public void testUpdateRecentCase() throws Exception {
        System.out.println("updateRecentCase");
        String oldName = "oldName";
        String oldPath = "C:\\oldPath";
        String newName = "newName";
        String newPath = "C:\\newPath";
        instance.addRecentCase(oldName, oldPath);
        instance.updateRecentCase(oldName, oldPath, newName, newPath);
        assertEquals(newName, instance.getRecentCaseNames()[0]);
        assertEquals(newPath, instance.getRecentCasePaths()[0]);
        assertEquals(1, instance.getTotalRecentCases());
    }

    /**
     * Test of getTotalRecentCases method, of class RecentCases.
     */
    @Test
    public void testGetTotalRecentCases() {
        System.out.println("getTotalRecentCases");
        int expResult = 0;
        int result = instance.getTotalRecentCases();
        assertEquals(expResult, result);
        instance.addRecentCase("name", "path");
        result = instance.getTotalRecentCases();
        expResult = 1;
        assertEquals(expResult, result);
    }

    /**
     * Test of removeRecentCase method, of class RecentCases.
     */
    @Test
    public void testRemoveRecentCase() {
        System.out.println("removeRecentCase");
        String name = "name";
        String path = "path";
        String name1 = "name1";
        String path1 = "path1";
        instance.addRecentCase(name, path);
        instance.addRecentCase(name1, path1);
        instance.removeRecentCase(name, path);
        assertEquals(1, instance.getTotalRecentCases());
        instance.removeRecentCase(name, path);
        assertEquals(1, instance.getTotalRecentCases());
        instance.removeRecentCase(name1, path1);
        assertEquals(0, instance.getTotalRecentCases());
    }

    /**
     * Test of getRecentCaseNames method, of class RecentCases.
     */
    @Test
    public void testGetRecentCaseNames() {
        System.out.println("getRecentCaseNames");
        String[] expResult = {"","","","",""};
        String[] result = instance.getRecentCaseNames();
        assertArrayEquals(expResult, result);
        String name = "name";
        String path = "C:\\path";
        String name1 = "name1";
        String path1 = "C:\\path1";
        instance.addRecentCase(name, path);
        instance.addRecentCase(name1, path1);
        String[] expResult1 = {name1,name,"","",""};
        String[] result1 = instance.getRecentCaseNames();
        assertArrayEquals(expResult1, result1);
    }

    /**
     * Test of getRecentCasePaths method, of class RecentCases.
     */
    @Test
    public void testGetRecentCasePaths() {
        System.out.println("getRecentCasePaths");
        String[] expResult = {"","","","",""};
        String[] result = instance.getRecentCasePaths();
        assertArrayEquals(expResult, result);
        String name = "name";
        String path = "C:\\path";
        String name1 = "name1";
        String path1 = "C:\\path1";
        instance.addRecentCase(name, path);
        instance.addRecentCase(name1, path1);
        String[] expResult1 = {path1, path,"","",""};
        String[] result1 = instance.getRecentCasePaths();
        assertArrayEquals(expResult1, result1);
    }

    /**
     * Test of getName method, of class RecentCases.
     */
    @Test
    public void testGetName() {
        System.out.println("getName");
        String result = instance.getName();
        assertNotNull(result);
    }    
    
    
    
    /**
     * Regression tests for TSK-227
     * Make sure that paths are normalized, so that different representations of
     * the same path don't result in duplicates.
     */
    @Test
    public void testNormalizePathAddRecentCase1() {
        System.out.println("normalizePathAddRecentCase1");
        String name = "name";
        String path = "C:\\biig-case\\biig-case.aut";
        String oddPath = "c:\\\\biig-case\\biig-case.aut";
        instance.addRecentCase(name, path);
        instance.addRecentCase(name, oddPath);
        assertEquals(1, instance.getTotalRecentCases());
    }
    @Test
    public void testNormalizePathAddRecentCase2() {
        System.out.println("normalizePathAddRecentCase2");
        String name = "name";
        String path = "C:\\biig-case\\biig-case.aut";
        String oddPath = "c:\\\\biig-case\\biig-case.aut";
        instance.addRecentCase(name, oddPath);
        instance.addRecentCase(name, path);
        assertEquals(1, instance.getTotalRecentCases());
    }
    @Test
    public void testNormalizePathUpdateRecentCase1() throws Exception {
        System.out.println("normalizePathUpdateRecentCase1");
        String oldName = "oldName";
        String oldPath = "C:\\biig-case\\biig-case.aut";
        String oddOldPath = "c:\\\\biig-case\\biig-case.aut";
        String newName = "newName";
        String newPath = "newPath";
        instance.addRecentCase(oldName, oldPath);
        instance.updateRecentCase(oldName, oddOldPath, newName, newPath);
        assertEquals(1, instance.getTotalRecentCases());
    }
    @Test
    public void testNormalizePathUpdateRecentCase2() throws Exception {
        System.out.println("normalizePathUpdateRecentCase2");
        String oldName = "oldName";
        String oldPath = "C:\\biig-case\\biig-case.aut";
        String oddOldPath = "c:\\\\biig-case\\biig-case.aut";
        String newName = "newName";
        String newPath = "newPath";
        instance.addRecentCase(oldName, oddOldPath);
        instance.updateRecentCase(oldName, oldPath, newName, newPath);
        assertEquals(1, instance.getTotalRecentCases());
    }
    @Test
    public void testNormalizePathRemoveRecentCase1() {
        System.out.println("normalizePathRemoveRecentCase1");
        String name = "name";
        String path = "C:\\biig-case\\biig-case.aut";
        String oddPath = "c:\\\\biig-case\\biig-case.aut";
        instance.addRecentCase(name, path);
        instance.removeRecentCase(name, oddPath);
        assertEquals(0, instance.getTotalRecentCases());
    }
    @Test
    public void testNormalizePathRemoveRecentCase2() {
        System.out.println("normalizePathRemoveRecentCase2");
        String name = "name";
        String path = "C:\\biig-case\\biig-case.aut";
        String oddPath = "c:\\\\biig-case\\biig-case.aut";
        instance.addRecentCase(name, oddPath);
        instance.removeRecentCase(name, path);
        assertEquals(0, instance.getTotalRecentCases());
    }
    

}