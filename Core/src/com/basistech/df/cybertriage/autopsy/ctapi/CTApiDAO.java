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

import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthTokenRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthTokenResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.AuthenticatedRequestData;
import com.basistech.df.cybertriage.autopsy.ctapi.json.CTCloudBean;
import com.basistech.df.cybertriage.autopsy.ctapi.json.CTCloudBeanResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.DecryptedLicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.FileReputationRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.json.MetadataUploadRequest;
import com.basistech.df.cybertriage.autopsy.ctapi.util.CTHostIDGenerationUtil;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.CollectionUtils;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 *
 * Data access layer for handling the CT api.
 */
public class CTApiDAO {

    private static final String LICENSE_REQUEST_PATH = "/_ah/api/license/v1/activate";
    private static final String AUTH_TOKEN_REQUEST_PATH = "/_ah/api/auth/v2/generate_token";
    private static final String CTCLOUD_SERVER_HASH_PATH = "/_ah/api/reputation/v1/query/file/hash/md5?query_types=CORRELATION,MALWARE";
    private static final String CTCLOUD_UPLOAD_FILE_METADATA_PATH = "/_ah/api/reputation/v1/upload/meta";

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
                .setProduct(AUTOPSY_PRODUCT)
                .setTimeZoneId(UserPreferences.getInferredUserTimeZone());

        return httpClient.doPost(LICENSE_REQUEST_PATH, licenseRequest, LicenseResponse.class);

    }

    public AuthTokenResponse getAuthToken(DecryptedLicenseResponse decrypted) throws CTCloudException {
        return getAuthToken(decrypted, false);
    }

    public AuthTokenResponse getAuthToken(DecryptedLicenseResponse decrypted, boolean fileUpload) throws CTCloudException {
        AuthTokenRequest authTokenRequest = new AuthTokenRequest()
                .setAutopsyVersion(getAppVersion())
                .setRequestFileUpload(fileUpload)
                .setBoostLicenseId(decrypted.getBoostLicenseId())
                .setHostId(decrypted.getLicenseHostId());

        return httpClient.doPost(AUTH_TOKEN_REQUEST_PATH, authTokenRequest, AuthTokenResponse.class);
    }

    public void uploadFile(String url, String fileName, InputStream fileIs) throws CTCloudException {
        httpClient.doFileUploadPost(url, fileName, fileIs);
    }
    
    public void uploadMeta(AuthenticatedRequestData authenticatedRequestData, MetadataUploadRequest metaRequest) throws CTCloudException {
        httpClient.doPost(CTCLOUD_UPLOAD_FILE_METADATA_PATH, getAuthParams(authenticatedRequestData), metaRequest, null);
    }

    private static Map<String, String> getAuthParams(AuthenticatedRequestData authenticatedRequestData) {
        return new HashMap<String, String>() {
            {
                put("api_key", authenticatedRequestData.getApiKey());
                put("token", authenticatedRequestData.getToken());
                put("host_id", authenticatedRequestData.getHostId());
            }
        };
    }

    public List<CTCloudBean> getReputationResults(AuthenticatedRequestData authenticatedRequestData, List<String> md5Hashes) throws CTCloudException {
        if (CollectionUtils.isEmpty(md5Hashes)) {
            return Collections.emptyList();
        }

        FileReputationRequest fileRepReq = new FileReputationRequest()
                .setHashes(md5Hashes);

        CTCloudBeanResponse resp = httpClient.doPost(
                CTCLOUD_SERVER_HASH_PATH,
                getAuthParams(authenticatedRequestData),
                fileRepReq,
                CTCloudBeanResponse.class
        );

        return resp == null || resp.getItems() == null
                ? Collections.emptyList()
                : resp.getItems();
    }
}
