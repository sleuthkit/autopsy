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
package com.basistech.df.cybertriage.autopsy.ctoptions.ctcloud;

import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseInfo;
import com.basistech.df.cybertriage.autopsy.ctapi.json.LicenseResponse;
import com.basistech.df.cybertriage.autopsy.ctapi.util.LicenseDecryptorUtil;
import com.basistech.df.cybertriage.autopsy.ctapi.util.ObjectMapperUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private static final String MALWARE_INGEST_SETTINGS_FILENAME = "MalwareIngestSettings.json";

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
                if (licenseResponse != null) {
                    objectMapper.writeValue(licenseFile, licenseResponse);
                } else if (licenseFile.exists()) {
                    Files.delete(licenseFile.toPath());
                }
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

        public synchronized boolean saveMalwareSettings(MalwareIngestSettings malwareIngestSettings) {
        if (malwareIngestSettings != null) {
            File settingsFile = getMalwareIngestFile();
            try {
                settingsFile.getParentFile().mkdirs();
                if (licenseResponse != null) {
                    objectMapper.writeValue(licenseFile, licenseResponse);
                } else if (licenseFile.exists()) {
                    Files.delete(licenseFile.toPath());
                }
                return true;
            } catch (IOException ex) {
                logger.log(Level.WARNING, "There was an error writing CyberTriage license to file: " + licenseFile.getAbsolutePath(), ex);
            }
        }

        return false;
    }

    public synchronized MalwareIngestSettings loadMalwareIngestSettings() {
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
    
    private File getCTLicenseFile() {
        return Paths.get(PlatformUtil.getModuleConfigDirectory(), CT_SETTINGS_DIR, CT_LICENSE_FILENAME).toFile();
    }

    private File getMalwareIngestFile() {
        return Paths.get(PlatformUtil.getModuleConfigDirectory(), CT_SETTINGS_DIR, MALWARE_INGEST_SETTINGS_FILENAME).toFile();
    }
}
