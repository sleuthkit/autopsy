/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctapi;

import java.net.URI;

// TODO take out anything sensitive or not used
final public class Constants {

    public static final String CYBER_TRIAGE = "CyberTriage";

    public static final String IS_MEMORY_IMAGE = "IS_MEMORY_IMAGE";


    public static final String SSLTEST_URL = "https://www2.cybertriage.com/ssl_test.html";


    

    public static final String CT_CLOUD_DEV_SERVER = "https://cyber-triage-dev.appspot.com";
    
    // TODO put back
    public static final String CT_CLOUD_SERVER = CT_CLOUD_DEV_SERVER; //"https://rep1.cybertriage.com";
    
    /**
     * Link to watch demo video
     * @since 3.1.0 
     */
    public static final String DEMO_VIDEO_URL = "https://www.cybertriage.com/video/cyber-triage-demo-video/?utm_source=Cyber+Triage+Tool&utm_campaign=Eval+Demo+Video";
    
    /**
     * Link request quote
     * @since 3.1.0 
     */
    public static final String REQUEST_QUOTE_URL = "https://www.cybertriage.com/request-quote/?utm_source=Cyber+Triage+Tool&utm_campaign=Eval+Quote";
    
    /**
     * Latest help document URL
     * @since 3.2.0
    */
    public static final URI  USER_GUIDE_LATEST_URL = URI.create("https://docs.cybertriage.com/en/latest/?utm_source=Cyber+Triage+Tool&utm_campaign=Help+Docs");
    
     /**
     * Visit website URL
     * @since 3.1.0 
     */
    public static final String VISIT_WEBSITE_URL ="https://www.cybertriage.com/eval_data_202109/?utm_source=Cyber+Triage+Tool&utm_campaign=Eval+Data+Button";
    
 
    /**
     * URL for visiting the website after the data is ingested on the dashboard. 
     */
    public static final String EVAL_WEBSITE_AUTO_URL = "https://www.cybertriage.com/eval_data_202109_auto/?utm_source=Cyber+Triage+Tool&utm_campaign=Eval+Data+Auto/"; //CT-4045


    public static final String SUPPORT_AT_CYBERTRIAGE_DOT_COM = "support@cybertriage.com";

    public static final String SALES_AT_CYBERTRIAGE_DOT_COM = "sales@cybertriage.com";

    public final static String AUTODETECT = "Auto Detect";

    public final static int RESTAPI_PORT = 9443;

    public static final String INVALID_HOSTNAME_REQUEST = "Request rejected. Invalid host name. Hostname contains characters that are not allowed. \n"
                        + "Characters that are not allowed include `~!@#$&^*(){}[]\\\\|;'\",<>/? \n"
                        + "You may input the host IP address if the name is not resolving.";
    public static final String INVALID_HOSTNAME_UI = "Invalid host name. Hostname contains characters that are not allowed. \n"
                        + "Characters that are not allowed include `~!@#$&^*(){}[]\\\\|;'\",<>/?";
    
}
