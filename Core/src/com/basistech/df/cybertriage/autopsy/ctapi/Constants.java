/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2016 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi;

import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO take out anything sensitive or not used
final public class Constants {

    public static final String CYBER_TRIAGE = "CyberTriage";

    public static final String IS_MEMORY_IMAGE = "IS_MEMORY_IMAGE";


    public static final String SSLTEST_URL = "https://www2.cybertriage.com/ssl_test.html";


    public static final String CT_CLOUD_SERVER = "https://rep1.cybertriage.com";

    public static final String CT_CLOUD_DEV_SERVER = "https://cyber-triage-dev.appspot.com";
    
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
