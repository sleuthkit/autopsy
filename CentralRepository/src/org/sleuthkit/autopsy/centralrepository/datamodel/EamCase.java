/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.openide.util.NbBundle.Messages;

/**
 *
 * Used to store info about a case.
 *
 */
public class EamCase implements Serializable {

    private static long serialVersionUID = 1L;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss (z)");

    private int ID;
    private String caseUUID;
    private EamOrganization org;
    private String displayName;
    private String creationDate;
    private String caseNumber;
    private String examinerName;
    private String examinerEmail;
    private String examinerPhone;
    private String notes;

    public EamCase(String caseUUID, String displayName) {
        this(-1, caseUUID, null, displayName, DATE_FORMAT.format(new Date()), "", "", "", "", "");
    }

    public EamCase(int ID,
            String caseUUID,
            EamOrganization org,
            String displayName,
            String creationDate,
            String caseNumber,
            String examinerName,
            String examinerEmail,
            String examinerPhone,
            String notes) {
        this.ID = ID;
        this.caseUUID = caseUUID;
        this.org = org;
        this.displayName = displayName;
        this.creationDate = creationDate;
        this.caseNumber = caseNumber;
        this.examinerName = examinerName;
        this.examinerEmail = examinerEmail;
        this.examinerPhone = examinerPhone;
        this.notes = notes;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("(");
        str.append("ID=").append(Integer.toString(getID()));
        str.append(",UUID=").append(getCaseUUID());
        str.append(",organization=").append(getOrg().toString());
        str.append(",displayName=").append(getDisplayName());
        str.append(",creationDate=").append(getCreationDate());
        str.append(",caseNumber=").append(getCaseNumber());
        str.append(",examinerName=").append(getExaminerName());
        str.append(",examinerEmail=").append(getExaminerEmail());
        str.append(",examinerPhone=").append(getExaminerPhone());
        str.append(",notes=").append(getNotes());
        str.append(")");
        return str.toString();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.caseUUID=Case UUID: "})
    public String getTitleCaseUUID() {
        return Bundle.EnterpriseArtifactsManagerCase_title_caseUUID();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.creationDate=Creation Date: "})
    public String getTitleCreationDate() {
        return Bundle.EnterpriseArtifactsManagerCase_title_creationDate();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.caseDisplayName=Case Name: "})
    public String getTitleCaseDisplayName() {
        return Bundle.EnterpriseArtifactsManagerCase_title_caseDisplayName();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.caseNumber=Case Number: "})
    public String getTitleCaseNumber() {
        return Bundle.EnterpriseArtifactsManagerCase_title_caseNumber();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.examinerName=Examiner Name: "})
    public String getTitleExaminerName() {
        return Bundle.EnterpriseArtifactsManagerCase_title_examinerName();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.examinerEmail=Examiner Email: "})
    public String getTitleExaminerEmail() {
        return Bundle.EnterpriseArtifactsManagerCase_title_examinerEmail();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.examinerPhone=Examiner Phone: "})
    public String getTitleExaminerPhone() {
        return Bundle.EnterpriseArtifactsManagerCase_title_examinerPhone();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.org=Organization: "})
    public String getTitleOrganization() {
        return Bundle.EnterpriseArtifactsManagerCase_title_org();
    }

    @Messages({"EnterpriseArtifactsManagerCase.title.notes=Notes: "})
    public String getTitleNotes() {
        return Bundle.EnterpriseArtifactsManagerCase_title_notes();
    }

    public String getCaseDetailsOptionsPaneDialog() {
        StringBuilder content = new StringBuilder();
        content.append(getTitleCaseUUID()).append(getCaseUUID().toString()).append("\n");
        content.append(getTitleCaseDisplayName()).append(getDisplayName()).append("\n");
        content.append(getTitleCreationDate()).append(getCreationDate()).append("\n");
        content.append(getTitleCaseNumber()).append(getCaseNumber()).append("\n");
        content.append(getTitleExaminerName()).append(getExaminerName()).append("\n");
        content.append(getTitleExaminerEmail()).append(getExaminerEmail()).append("\n");
        content.append(getTitleExaminerPhone()).append(getExaminerPhone()).append("\n");
        content.append(getTitleNotes()).append(getNotes()).append("\n");

        return content.toString();
    }

    /**
     * @return the ID
     */
    public int getID() {
        return ID;
    }

    /**
     * @param ID the ID to set
     */
    public void setID(int ID) {
        this.ID = ID;
    }

    /**
     * @return the caseUUID
     */
    public String getCaseUUID() {
        return caseUUID;
    }

    /**
     * @param caseUUID the caseUUID to set
     */
    public void setCaseUUID(String caseUUID) {
        this.caseUUID = caseUUID;
    }

    /**
     * @return the org
     */
    public EamOrganization getOrg() {
        return org;
    }

    /**
     * @param org the org to set
     */
    public void setOrg(EamOrganization org) {
        this.org = org;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the creationDate
     */
    public String getCreationDate() {
        return creationDate;
    }

    /**
     * @param creationDate the creationDate to set
     */
    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * @return the caseNumber
     */
    public String getCaseNumber() {
        return caseNumber;
    }

    /**
     * @param caseNumber the caseNumber to set
     */
    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    /**
     * @return the examinerName
     */
    public String getExaminerName() {
        return examinerName;
    }

    /**
     * @param examinerName the examinerName to set
     */
    public void setExaminerName(String examinerName) {
        this.examinerName = examinerName;
    }

    /**
     * @return the examinerEmail
     */
    public String getExaminerEmail() {
        return examinerEmail;
    }

    /**
     * @param examinerEmail the examinerEmail to set
     */
    public void setExaminerEmail(String examinerEmail) {
        this.examinerEmail = examinerEmail;
    }

    /**
     * @return the examinerPhone
     */
    public String getExaminerPhone() {
        return examinerPhone;
    }

    /**
     * @param examinerPhone the examinerPhone to set
     */
    public void setExaminerPhone(String examinerPhone) {
        this.examinerPhone = examinerPhone;
    }

    /**
     * @return the notes
     */
    public String getNotes() {
        return notes;
    }

    /**
     * @param notes the notes to set
     */
    public void setNotes(String notes) {
        this.notes = notes;
    }

}
