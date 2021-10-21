/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Attempts to determine a user's role on a domain based on the URL.
 */
class ExtractWebAccountType extends Extract {

    private static final Logger logger = Logger.getLogger(ExtractWebAccountType.class.getName());
    private final IngestJobContext context;

    ExtractWebAccountType(IngestJobContext context) {
        super(NbBundle.getMessage(ExtractWebAccountType.class, "ExtractWebAccountType.moduleName.text"), context);
        this.context = context;
    }

    private static final List<BlackboardArtifact.Type> QUERY_ARTIFACTS = Arrays.asList(
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY),
            new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT)
    );

    private void extractDomainRoles(Content dataSource) {
        try {
            // Get web history blackboard artifacts
            Collection<BlackboardArtifact> listArtifacts = currentCase.getSleuthkitCase().getBlackboard().getArtifacts(
                    QUERY_ARTIFACTS, Arrays.asList(dataSource.getId()));

            logger.log(Level.INFO, "Processing {0} blackboard artifacts.", listArtifacts.size()); //NON-NLS

            // Set up collector for roles
            RoleProcessor roleProcessor = new RoleProcessor(context);

            // Process each URL
            for (BlackboardArtifact artifact : listArtifacts) {
                if (context.dataSourceIngestIsCancelled()) {
                    return;
                }

                findRolesForUrl(artifact, roleProcessor);
            }

            // Create artifacts
            roleProcessor.createArtifacts();

        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Encountered error retrieving artifacts for domain role analysis", e); //NON-NLS
        }
    }

    /**
     * Extract and store any role found in the given artifact.
     *
     * @param artifact      The original artifact
     * @param roleProcessor Object to collect and process domain roles.
     *
     * @throws TskCoreException
     */
    private void findRolesForUrl(BlackboardArtifact artifact, RoleProcessor roleProcessor) throws TskCoreException {

        BlackboardAttribute urlAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL));
        if (urlAttr == null) {
            return;
        }

        BlackboardAttribute domainAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN));
        if (domainAttr == null) {
            return;
        }

        String url = urlAttr.getValueString().toLowerCase();
        String domain = domainAttr.getValueString().toLowerCase();

        boolean roleFound = false;
        roleFound = findMyBbRole(url, domain, artifact, roleProcessor) || roleFound;
        roleFound = findPhpBbRole(url, domain, artifact, roleProcessor) || roleFound;
        roleFound = findJoomlaRole(url, domain, artifact, roleProcessor) || roleFound;
        roleFound = findWordPressRole(url, domain, artifact, roleProcessor) || roleFound;

        // if no other role for this url was found and it is a TSK_SERVICE_ACCOUNT, add a general user role.
        if (!roleFound && artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID()) {
            roleProcessor.addRole(domain, domain, Role.USER, url, artifact);
        }
    }

    /**
     * Extract myBB role.
     *
     * @param url           The full URL.
     * @param domain        The domain.
     * @param artifact      The original artifact.
     * @param roleProcessor Object to collect and process domain roles.
     *
     * @return True if a myBB role is found.
     */
    private boolean findMyBbRole(String url, String domain, BlackboardArtifact artifact, RoleProcessor roleProcessor) {
        String platformName = "myBB platform"; // NON-NLS

        if (url.contains("/admin/index.php")) {
            roleProcessor.addRole(domain, platformName, Role.ADMIN, url, artifact);
            return true;
        } else if (url.contains("/modcp.php")) {
            roleProcessor.addRole(domain, platformName, Role.MOD, url, artifact);
            return true;
        } else if (url.contains("/usercp.php")) {
            roleProcessor.addRole(domain, platformName, Role.USER, url, artifact);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Extract phpBB role.
     *
     * @param url           The full URL.
     * @param domain        The domain.
     * @param artifact      The original artifact.
     * @param roleProcessor Object to collect and process domain roles.
     *
     * @return True if a phpBB role is found.
     */
    private boolean findPhpBbRole(String url, String domain, BlackboardArtifact artifact, RoleProcessor roleProcessor) {
        String platformName = "phpBB platform"; // NON-NLS

        if (url.contains("/adm/index.php")) {
            roleProcessor.addRole(domain, platformName, Role.ADMIN, url, artifact);
            return true;
        } else if (url.contains("/mcp.php")) {
            roleProcessor.addRole(domain, platformName, Role.MOD, url, artifact);
            return true;
        } else if (url.contains("/ucp.php")) {
            roleProcessor.addRole(domain, platformName, Role.USER, url, artifact);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Extract Joomla role.
     *
     * @param url           The full URL.
     * @param domain        The domain.
     * @param artifact      The original artifact.
     * @param roleProcessor Object to collect and process domain roles.
     *
     * @return True if a Joomla role is found.
     */
    private boolean findJoomlaRole(String url, String domain, BlackboardArtifact artifact, RoleProcessor roleProcessor) {
        String platformName = "Joomla platform"; // NON-NLS

        if (url.contains("/administrator/index.php")) { // NON-NLS
            roleProcessor.addRole(domain, platformName, Role.ADMIN, url, artifact);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Extract WordPress role.
     *
     * @param url           The full URL.
     * @param domain        The domain.
     * @param artifact      The original artifact.
     * @param roleProcessor Object to collect and process domain roles.
     *
     * @return True if a WordPress role is found.
     */
    private boolean findWordPressRole(String url, String domain, BlackboardArtifact artifact, RoleProcessor roleProcessor) {
        String platformName = "WordPress platform"; // NON-NLS

        // For WordPress, any logged in user can get to /wp-admin/, /wp-admin/index.php and /wp-admin/profile.php, so
        // assume that any other .php file will indicate an administrator
        if (url.contains("/wp-admin/")) {

            if (url.endsWith("/wp-admin/")
                    || url.contains("/wp-admin/index.php")
                    || url.contains("/wp-admin/profile.php")) {
                roleProcessor.addRole(domain, platformName, Role.USER, url, artifact);
                return true;
            } else {
                roleProcessor.addRole(domain, platformName, Role.ADMIN, url, artifact);
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    void process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        extractDomainRoles(dataSource);
    }

    /**
     * Collects data for making blackboard artifacts.
     *
     * We only want a max of one role per domain, and the role should be the
     * highest level found. The full URL and associated file will belong to the
     * first artifact found with the recorded level.
     */
    private class RoleProcessor {

        private final IngestJobContext context;
        private final Map<RoleKey, DomainRole> roles = new HashMap<>();

        RoleProcessor(IngestJobContext context) {
            this.context = context;
        }

        /**
         * Add a role to the map if: - This is the first time we've seen this
         * domain/platform - The level of the role is higher than previously
         * seen for this domain/platform
         *
         * @param domain   The domain.
         * @param platform The probable platform for this role.
         * @param role     The role level.
         * @param url      The URL (stored for efficiency).
         * @param artifact The original blackboard artifact the URL came from.
         */
        void addRole(String domain, String platform, Role role, String url, BlackboardArtifact artifact) {
            RoleKey key = new RoleKey(domain, platform);
            if ((!roles.containsKey(key))
                    || (roles.containsKey(key) && (role.getRank() > roles.get(key).getRole().getRank()))) {
                roles.put(key, new DomainRole(domain, platform, role, url, artifact));
            }
        }

        /**
         * Create artifacts for the domain roles.
         */
        void createArtifacts() {

            if (roles.isEmpty()) {
                logger.log(Level.INFO, "Didn't find any web accounts.");
                return;
            } else {
                logger.log(Level.INFO, "Found {0} web accounts.", roles.keySet().size());
            }

            try {
                List<BlackboardArtifact> artifactList = new ArrayList<>();
                for (RoleKey key : roles.keySet()) {
                    if (context.dataSourceIngestIsCancelled()) {
                        return;
                    }

                    DomainRole role = roles.get(key);

                    AbstractFile file = tskCase.getAbstractFileById(role.getArtifact().getObjectID());
                    if (file == null) {
                        continue;
                    }

                    String desc = role.getRole().getDesc() + " (" + role.getPlatform() + ")"; // NON-NLS

                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN,
                            NbBundle.getMessage(this.getClass(),
                                    "ExtractWebAccountType.parentModuleName"), role.getDomain()));
                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT,
                            NbBundle.getMessage(this.getClass(),
                                    "ExtractWebAccountType.parentModuleName"), desc));
                    bbattributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL,
                            NbBundle.getMessage(this.getClass(),
                                    "ExtractWebAccountType.parentModuleName"), role.getUrl()));

                    artifactList.add(createArtifactWithAttributes(BlackboardArtifact.Type.TSK_WEB_ACCOUNT_TYPE, file, bbattributes));
                }

                if (!context.dataSourceIngestIsCancelled()) {
                    postArtifacts(artifactList);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating web accounts", ex);
            }
        }
    }

    /**
     * Possible roles with rank and display name.
     */
    @NbBundle.Messages({
        "ExtractWebAccountType.role.user=User role",
        "ExtractWebAccountType.role.moderator=Moderator role",
        "ExtractWebAccountType.role.admin=Administrator role"
    })
    private enum Role {
        USER(Bundle.ExtractWebAccountType_role_user(), 0),
        MOD(Bundle.ExtractWebAccountType_role_moderator(), 1),
        ADMIN(Bundle.ExtractWebAccountType_role_admin(), 2);

        private final String desc;
        private final int rank;

        Role(String desc, int rank) {
            this.desc = desc;
            this.rank = rank;
        }

        String getDesc() {
            return desc;
        }

        int getRank() {
            return rank;
        }
    }

    /**
     * Holds key to retrieve data for a given domain/platform.
     */
    private class RoleKey {

        private final String domain;
        private final String platform;

        RoleKey(String domain, String platform) {
            this.domain = domain;
            this.platform = platform;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RoleKey)) {
                return false;
            }

            RoleKey otherKey = (RoleKey) other;
            return (domain.equals(otherKey.domain)
                    && platform.equals(otherKey.platform));
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.domain);
            hash = 79 * hash + Objects.hashCode(this.platform);
            return hash;
        }
    }

    /**
     * Holds full data for a domain role
     */
    private class DomainRole {

        final String domain;
        final String platform;
        final Role role;
        final String url;
        final BlackboardArtifact artifact;

        DomainRole(String domain, String platform, Role role, String url, BlackboardArtifact artifact) {
            this.domain = domain;
            this.role = role;
            this.platform = platform;
            this.url = url;
            this.artifact = artifact;
        }

        String getDomain() {
            return domain;
        }

        String getPlatform() {
            return platform;
        }

        Role getRole() {
            return role;
        }

        String getUrl() {
            return url;
        }

        BlackboardArtifact getArtifact() {
            return artifact;
        }
    }
}
