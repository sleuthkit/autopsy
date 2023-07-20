/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2020 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi;


import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 *
 * @author rishwanth
 */


public class CTCloudException extends Exception{
    private final ErrorCode errorCode;
    
    public enum ErrorCode {
        BAD_REQUEST("CT-400", "Unknown or Bad request. Please contact Basis support at " + Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM + " for help diagnosing the problem."),
        INVALID_KEY("CT-401", "An invalid license ID was used to access CyberTriage Cloud Service. Please contact Basis support " + Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM + " for help diagnosing the problem."),
        GATEWAY_TIMEOUT("CT-504", "Request to CyberTriage Cloud Service timed out. Please retry after some time. If issue persists, please contact Basis support at " + Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM + " for assistance."),
        UN_AUTHORIZED("CT-403", "An authorization error occurred. Please contact Basis support " + Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM + " for help diagnosing the problem."),
        PROXY_UNAUTHORIZED("CT-407", "Proxy authentication failed. Please validate the connection settings from the Options panel Proxy Settings."),
        TEMP_UNAVAILABLE("CT-500", "CyberTriage Cloud Service temporarily unavailable; please try again later. If this problem persists, contact Basis support at " + Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM),
        UNKNOWN("CT-080", "Unknown error while communicating with CyberTriage Cloud Service. If this problem persists, contact Basis support at "+ Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM +" for assistance."),
        UNKNOWN_HOST("CT-081", "Unknown host error. If this problem persists, contact Basis support at "+ Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM +" for assistance."),
        NETWORK_ERROR("CT-015", "Error connecting to CyberTriage Cloud.\n"
                + "Check your firewall or proxy settings.\n"
                + "Contact Support (support@cybertriage.com) for further assistance");
        private final String errorcode;
        private final String description;
        
        private ErrorCode(String errorcode, String description) {
            this.errorcode = errorcode;
            this.description = description;
        }

        public String getCode() {
            return errorcode;
        }

        public String getDescription() {
            return description;
        }

    }

    public CTCloudException(CTCloudException.ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }
    
    public CTCloudException(CTCloudException.ErrorCode errorCode, Throwable throwable) {
        super(errorCode.name(), throwable);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getErrorDetails() {
        if(getErrorCode() == CTCloudException.ErrorCode.UNKNOWN && Objects.nonNull(getCause())){
            return String.format("Malware scan error %s occurred. Please try \"Re Scan\" from the dashboard to attempt Malware scaning again. "
                    + "\nPlease contact Basis support at %s for help if the problem presists.",
                    StringUtils.isNotBlank(getCause().getLocalizedMessage()) ? "("+getCause().getLocalizedMessage()+")": "(Unknown)",
                    Constants.SUPPORT_AT_CYBERTRIAGE_DOT_COM );
        }else {
            return getErrorCode().getDescription();
        }
    }
    
    /*
     * Attempts to find a more specific error code than "Unknown" for the given exception.
     */
    public static ErrorCode parseUnknownException(Throwable throwable) {
        
        String stackTrace = ExceptionUtils.getStackTrace(throwable);
        if (stackTrace.contains("UnknownHostException")) {
            return ErrorCode.UNKNOWN_HOST;
        }
        
        return ErrorCode.UNKNOWN;
    }
}
