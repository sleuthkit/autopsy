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
package org.sleuthkit.autopsy.recentactivity;

import java.sql.*;

/**
 *
 * @author Alex
 */
public class dbconnect extends sqlitedbconnect {

    private String sDriverForclass = "org.sqlite.JDBC";

    public dbconnect(String sDriverForClass, String sUrlKey) throws Exception {
        init(sDriverForClass, sUrlKey);
        //Statement stmt = conn.createStatement();
        //String selecthistory = "SELECT moz_historyvisits.id,url,title,visit_count,visit_date,from_visit,rev_host FROM moz_places, moz_historyvisits WHERE moz_places.id = moz_historyvisits.place_id AND hidden = 0";
        // ResultSet rs = stmt.executeQuery(selecthistory); 

    }
}
