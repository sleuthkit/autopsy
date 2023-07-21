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
package com.basistech.df.cybertriage.autopsy.ctoptions.ctcloud;

import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseInfo;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.util.LicenseDecryptorUtil;
import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Handles persisting CT Settings.
 */
public class CTLicensePersistence {

    private static final String CT_SETTINGS_DIR = "CyberTriage";
    private static final String CT_LICENSE_FILENAME = "CyberTriageLicense.json";

    private static final Logger logger = Logger.getLogger(CTLicensePersistence.class.getName());

    private static final CTLicensePersistence instance = new CTLicensePersistence();

    private final ObjectMapper objectMapper = ObjectMapperUtil.getInstance().getDefaultObjectMapper();

    public static CTLicensePersistence getInstance() {
        return instance;
    }

    public synchronized boolean saveLicenseResponse(LicenseResponse licenseResponse) {
        if (licenseResponse != null) {
            File licenseFile = getCTLicenseFile();
            try {
                licenseFile.getParentFile().mkdirs();
                objectMapper.writeValue(licenseFile, licenseResponse);
                return true;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "There was an error writing CyberTriage license to file: " + licenseFile.getAbsolutePath(), ex);
            }
        }

        return false;
    }

    public synchronized Optional<LicenseResponse> loadLicenseResponse() {
        Optional<LicenseResponse> toRet = Optional.empty();
        File licenseFile = getCTLicenseFile();
        if (licenseFile.exists() && licenseFile.isFile()) {
            try {
                toRet = Optional.ofNullable(objectMapper.readValue(licenseFile, LicenseResponse.class));
            } catch (IOException ex) {
                logger.log(Level.WARNING, "There was an error reading CyberTriage license to file: " + licenseFile.getAbsolutePath(), ex);
            }
        }

        return toRet;
    }

    public synchronized Optional<LicenseInfo> loadLicenseInfo() {
        return loadLicenseResponse().flatMap((license) -> {
            try {
                return Optional.ofNullable(LicenseDecryptorUtil.getInstance().createLicenseInfo(license));
            } catch (JsonProcessingException | LicenseDecryptorUtil.InvalidLicenseException ex) {
                logger.log(Level.WARNING, "There was an error decrypting license data from license file", ex);
                return Optional.empty();
            }
        });
    }

    private File getCTLicenseFile() {
        return Paths.get(PlatformUtil.getModuleConfigDirectory(), CT_SETTINGS_DIR, CT_LICENSE_FILENAME).toFile();
    }
}
