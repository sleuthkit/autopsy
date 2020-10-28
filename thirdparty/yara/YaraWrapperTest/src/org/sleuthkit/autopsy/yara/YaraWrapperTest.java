/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.yara;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.sleuthkit.autopsy.yara.YaraJNIWrapper;
import org.sleuthkit.autopsy.yara.YaraWrapperException;

/**
 * Tests the YaraJNIWrapper code.
 */
public class YaraWrapperTest {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Please supply two arguments, a yara compiled rule path and a path to the file to scan.");
            return;
        }

        TestFileRuleMatch(args[0], args[1]);
    }

    /**
     * Call the YaraJNIWrapper FindRuleMatch with the given path and output the
     * results to the cl.
     *
     * @param compiledRulePath Path to yara compiled rule file
     * @param filePath         Path to file
     */
    private static void TestFileRuleMatch(String compiledRulePath, String filePath) {
        Path path = Paths.get(filePath);

        try {
            byte[] data = Files.readAllBytes(path);

            List<String> list = YaraJNIWrapper.FindRuleMatch(compiledRulePath, data);

            if (list != null) {
                if (list.isEmpty()) {
                    System.out.println("FindRuleMatch return an empty list");
                } else {
                    for (String s : list) {
                        System.out.println("Matching Rules:");
                        System.out.println(s);
                    }
                }
            } else {
                System.out.println("FindRuleMatch return a null list");
            }

        } catch (IOException | YaraWrapperException ex) {
            ex.printStackTrace();
        }
    }

}
