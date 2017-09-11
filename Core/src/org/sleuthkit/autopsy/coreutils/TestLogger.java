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
package org.sleuthkit.autopsy.coreutils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Formatter;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.logging.Level;

/*
 * Toolbar button for testing logging. Not a normal part of application.
 */
final class TestLogger implements ActionListener {

    static final Logger logger = Logger.getLogger(TestLogger.class.getName());
    Formatter fmt;

    @Override
    public void actionPerformed(ActionEvent e) {

        logger.log(Level.WARNING, "Testing log!", new Exception(new Exception(new Exception(new Exception("original reason with asdfasdfasdfasdfasd fasdfasdfasdf sdfasdfasdfa asdfasdf asdfa sdfas ", new Exception("more original reason")))))); //NON-NLS
        //throw new RuntimeException("othe");

        //logger.log(Level.WARNING, "Testing log!");
    }
}
