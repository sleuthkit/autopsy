/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.xryparser;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author dsmyda
 */
public class XRYReportFolderTest {
    
    private final Path reportDirectory = Paths.get("C:", "Users", "dsmyda", "Downloads", "2019-10-23-XRYSamples");
    private final Path notAReportDirectory = Paths.get("C:", "Users", "dsmyda", "Downloads", "Not-2019-10-23-XRYSamples");
    private final Path biggerReportDirectory = Paths.get("C:", "Users", "dsmyda", "Documents", "personal");
    
    public XRYReportFolderTest() {
    }

    /**
     * Test of getXRYReportFiles method, of class XRYReportFolder.
     */
//    @Test
//    public void testGetXRYReportFiles() {
//        System.out.println("getXRYReportFiles");
//        XRYReportFolder instance = null;
//        List<Path> expResult = null;
//        List<Path> result = instance.getXRYReportFiles();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of getOtherFiles method, of class XRYReportFolder.
     */
//    @Test
//    public void testGetOtherFiles() {
//        System.out.println("getOtherFiles");
//        XRYReportFolder instance = null;
//        List<Path> expResult = null;
//        List<Path> result = instance.getOtherFiles();
//        assertEquals(expResult, result);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    /**
     * Test of isXRYReportFolder method, of class XRYReportFolder.
     */
    @Test
    public void testIsXRYReportFolder() throws Exception {
        assertTrue("Not flagged as an xry folder, but should be", XRYReportFolder.isXRYReportFolder(reportDirectory));
        assertFalse("Flagged as an xry folder, but shouldn't be", XRYReportFolder.isXRYReportFolder(notAReportDirectory));
        assertTrue("Not flagged as an xry folder, but should be", XRYReportFolder.isXRYReportFolder(biggerReportDirectory));
    }
    
}
