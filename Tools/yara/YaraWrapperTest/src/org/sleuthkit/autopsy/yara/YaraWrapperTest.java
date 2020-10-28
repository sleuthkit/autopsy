/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.yara;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.sleuthkit.autopsy.yara.YaraJNIWrapper;
import org.sleuthkit.autopsy.yara.YaraWrapperException;


public class YaraWrapperTest {

    private static String compiledRulePath = "C:\\Temp\\yara\\hello.compiled";
    private static String textFilePath = "C:\\Temp\\yara\\hello.txt";

    public static void main(String[] args) {
        Path path = Paths.get(textFilePath);
        try {
            byte[] data = Files.readAllBytes(path);

            List<String> list = YaraJNIWrapper.FindRuleMatch(compiledRulePath, data);

            for (String s : list) {
                System.out.println(s);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (YaraWrapperException ex) {
            System.out.println("it worked");
        }
    }

}
