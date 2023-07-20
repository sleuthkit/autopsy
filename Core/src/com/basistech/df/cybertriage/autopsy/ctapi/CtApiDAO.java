/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctapi;

import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthTokenRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthTokenResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.FileReputationResult;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.util.CTHostIDGenerationUtil;
import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 *
 * Data access layer for handling the CT api.
 */
public class CtApiDAO {

    private static final String LICENSE_REQUEST_PATH = "/_ah/api/license/v1/activate";
    private static final String AUTH_TOKEN_REQUEST_PATH = "/_ah/api/auth/v2/generate_token";

    private static final CtApiDAO instance = new CtApiDAO();
    private final ObjectMapper mapper = ObjectMapperUtil.getInstance().getDefaultObjectMapper();

    private CtApiDAO() {
    }

    public static CtApiDAO getInstance() {
        return instance;
    }
    
    private static String getAppVersion() {
        return Version.getName() + " " + Version.getVersion();
    }

    private <T> T doPost(String urlPath, Object requestBody, Class<T> responseTypeRef) throws CTCloudException {
        return null;
        // TODO
    }

    public LicenseResponse getLicenseInfo(String licenseString) throws CTCloudException {
        LicenseRequest licenseRequest = new LicenseRequest()
                .setBoostLicenseCode(licenseString)
                .setHostId(CTHostIDGenerationUtil.generateLicenseHostID())
                .setProduct(getAppVersion());
        
        return doPost(LICENSE_REQUEST_PATH, licenseRequest, LicenseResponse.class);

    }

    public AuthTokenResponse getAuthToken(String boostLicenseId) throws CTCloudException {
        AuthTokenRequest authTokenRequest = new AuthTokenRequest()
                .setAutopsyVersion(getAppVersion())
                .setRequestFileUpload(true)
                .setBoostLicenseId(boostLicenseId);
        
        return doPost(AUTH_TOKEN_REQUEST_PATH, authTokenRequest, AuthTokenResponse.class);
    }

    public List<FileReputationResult> getReputationResults(String authToken, List<String> md5Hashes) throws CTCloudException {
        // TODO
//        return cloudServiceApi.lookupFileResults(md5Hashes, HashTypes.md5);
        return null;
    }

    public enum ResultType {
        OK, SERVER_ERROR, NOT_AUTHORIZED
    }
}
