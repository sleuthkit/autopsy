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
import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthenticatedRequestData;
import com.basistech.df.cybertriage.autopsy.ctapi.json.CTCloudBean;
import com.basistech.df.cybertriage.autopsy.ctapi.json.CTCloudBeanResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.DecryptedLicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.FileReputationRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.util.CTHostIDGenerationUtil;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 *
 * Data access layer for handling the CT api.
 */
public class CTApiDAO {

    private static final String LICENSE_REQUEST_PATH = "/_ah/api/license/v1/activate";
    private static final String AUTH_TOKEN_REQUEST_PATH = "/_ah/api/auth/v2/generate_token";
    private static final String CTCLOUD_SERVER_HASH_PATH = "/_ah/api/reputation/v1/query/file/hash/md5?query_types=CORRELATION,MALWARE";
    private static final String AUTOPSY_PRODUCT = "AUTOPSY";
    
    private static final CTApiDAO instance = new CTApiDAO();
    

    private CTApiDAO() {
    }

    public static CTApiDAO getInstance() {
        return instance;
    }

    private static String getAppVersion() {
        return Version.getVersion();
    }
    
    private final CTCloudHttpClient httpClient = CTCloudHttpClient.getInstance();


    public LicenseResponse getLicenseInfo(String licenseString) throws CTCloudException {
        LicenseRequest licenseRequest = new LicenseRequest()
                .setBoostLicenseCode(licenseString)
                .setHostId(CTHostIDGenerationUtil.generateLicenseHostID())
                .setProduct(AUTOPSY_PRODUCT);

        return httpClient.doPost(LICENSE_REQUEST_PATH, licenseRequest, LicenseResponse.class);

    }

    public AuthTokenResponse getAuthToken(DecryptedLicenseResponse decrypted) throws CTCloudException {
        AuthTokenRequest authTokenRequest = new AuthTokenRequest()
                .setAutopsyVersion(getAppVersion())
                .setRequestFileUpload(false)
                .setBoostLicenseId(decrypted.getBoostLicenseId())
                .setHostId(decrypted.getLicenseHostId());

        return httpClient.doPost(AUTH_TOKEN_REQUEST_PATH, authTokenRequest, AuthTokenResponse.class);
    }

    public List<CTCloudBean> getReputationResults(AuthenticatedRequestData authenticatedRequestData, List<String> md5Hashes) throws CTCloudException {
        if (CollectionUtils.isEmpty(md5Hashes)) {
            return Collections.emptyList();
        }

        FileReputationRequest fileRepReq = new FileReputationRequest()
                .setApiKey(authenticatedRequestData.getApiKey())
                .setHostId(CTHostIDGenerationUtil.generateLicenseHostID())
                .setToken(authenticatedRequestData.getToken())
                .setHashes(md5Hashes);

        CTCloudBeanResponse resp = httpClient.doPost(CTCLOUD_SERVER_HASH_PATH, fileRepReq, CTCloudBeanResponse.class);
        return resp == null || resp.getItems() == null
                ? Collections.emptyList()
                : resp.getItems();
    }
}
