 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.report;

import java.lang.reflect.Method;

public class BrowserControl{
/**
* Method to Open the Browser with Given URL
* @param url
*/
public static void openUrl(String url){
String os = System.getProperty("os.name");
Runtime runtime=Runtime.getRuntime();
try{
// Block for Windows Platform
if (os.startsWith("Windows")){
String cmd = "rundll32 url.dll,FileProtocolHandler "+ url;
Process p = runtime.exec(cmd);
}
//Block for Mac OS
else if(os.startsWith("Mac OS")){
Class fileMgr = Class.forName("com.apple.eio.FileManager");
Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] {String.class});
openURL.invoke(null, new Object[] {url});
}
//Block for UNIX Platform
else {
String[] browsers = {"firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape" };
String browser = null;
for (int count = 0; count < browsers.length && browser == null; count++)
if (runtime.exec(new String[] {"which", browsers[count]}).waitFor() == 0)
browser = browsers[count];
if (browser == null)
throw new Exception("Could not find web browser");
else
runtime.exec(new String[] {browser, url});
}
}catch(Exception x){
System.err.println("Exception occurd while invoking Browser!");
x.printStackTrace();
}
}
}
