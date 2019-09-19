/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.stix;

import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

import java.util.List;
import java.util.ArrayList;

import org.mitre.cybox.objects.EmailMessage;
import org.mitre.cybox.objects.Address;

/**
 *
 */
class EvalEmailObj extends EvaluatableObject {

    private final EmailMessage obj;

    private List<BlackboardArtifact> finalHits;

    public EvalEmailObj(EmailMessage a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;

        finalHits = null;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        List<BlackboardArtifact> toHits = null;
        boolean hadToFields = false;
        List<BlackboardArtifact> ccHits = null;
        boolean hadCcFields = false;
        List<BlackboardArtifact> fromHits = null;
        boolean hadFromField = false;
        List<BlackboardArtifact> subjectHits = null;
        boolean hadSubjectField = false;

        if (obj.getHeader() != null) {
            if ((obj.getHeader().getTo() != null)
                    && (obj.getHeader().getTo().getRecipients() != null)
                    && (!obj.getHeader().getTo().getRecipients().isEmpty())) {
                for (Address addr : obj.getHeader().getTo().getRecipients()) {
                    if (addr.getAddressValue() != null) {

                        hadToFields = true;

                        try {
                            toHits = findArtifactsBySubstring(addr.getAddressValue(),
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO);
                        } catch (TskCoreException ex) {
                            addWarning(ex.getLocalizedMessage());
                        }
                    }
                }
            }

            if ((obj.getHeader().getCC() != null)
                    && (obj.getHeader().getCC().getRecipients() != null)
                    && (!obj.getHeader().getCC().getRecipients().isEmpty())) {
                for (Address addr : obj.getHeader().getCC().getRecipients()) {
                    if (addr.getAddressValue() != null) {

                        hadCcFields = true;

                        try {
                            ccHits = findArtifactsBySubstring(addr.getAddressValue(),
                                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CC);
                        } catch (TskCoreException ex) {
                            addWarning(ex.getLocalizedMessage());
                        }
                    }
                }
            }

            if ((obj.getHeader().getFrom() != null)
                    && (obj.getHeader().getFrom().getAddressValue() != null)) {

                hadFromField = true;

                try {
                    fromHits = findArtifactsBySubstring(obj.getHeader().getFrom().getAddressValue(),
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM);
                } catch (TskCoreException ex) {
                    addWarning(ex.getLocalizedMessage());
                }
            }

            if ((obj.getHeader().getSubject() != null)
                    && (obj.getHeader().getSubject().getValue() != null)) {

                hadSubjectField = true;

                try {
                    subjectHits = findArtifactsBySubstring(obj.getHeader().getSubject(),
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT);
                } catch (TskCoreException ex) {
                    addWarning(ex.getLocalizedMessage());
                }
            }
        }

        // Make sure at least one test had some data
        if ((!hadToFields) && (!hadFromField) && (!hadCcFields) && (!hadSubjectField)) {
            return new ObservableResult(id, "EmailMessage: Could not find any parsable EmailMessage fields " //NON-NLS
                    + getPrintableWarnings(),
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // Check if there were more fields that aren't currently supported
        String fieldNames = getListOfUnsupportedFields();
        if (fieldNames.length() > 0) {
            addWarning("Unsupported field(s) found: " + fieldNames); //NON-NLS
        }

        // Find the artifacts that matched all of the fields
        finalHits = null;
        boolean finalHitsStarted = false;

        if (hadToFields) {
            combineHits(toHits, finalHitsStarted);
            finalHitsStarted = true;
        }
        if (hadCcFields) {
            combineHits(ccHits, finalHitsStarted);
            finalHitsStarted = true;
        }
        if (hadFromField) {
            combineHits(fromHits, finalHitsStarted);
            finalHitsStarted = true;
        }
        if (hadSubjectField) {
            combineHits(subjectHits, finalHitsStarted);
            finalHitsStarted = true;
        }

        if (!finalHitsStarted) {
            // We didn't find any fields that could be evaluated
            return new ObservableResult(id, "EmailMessage: EmailObj parsing incomplete " + getPrintableWarnings(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // If there are any artifacts left in finalHits, we have a match
        if (finalHits.size() > 0) {
            List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
            for (BlackboardArtifact a : finalHits) {
                artData.add(new StixArtifactData(a.getObjectID(), id, "EmailMessage")); //NON-NLS
            }
            return new ObservableResult(id, "EmailMessage: " + finalHits.size() + " matching artifacts found " + getPrintableWarnings(), //NON-NLS
                    spacing, ObservableResult.ObservableState.TRUE, artData);
        } else {
            return new ObservableResult(id, "EmailMessage: No matching artifacts found " + getPrintableWarnings(), //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        }
    }

    /**
     * Add a set of hits to the final set of hits. Removes any artifacts that
     * aren't found in the new set. The final list is the artifacts found in all
     * sets.
     *
     * @param newHits          The new hits to add to the list
     * @param finalHitsStarted Whether we've started the list or not
     */
    private void combineHits(List<BlackboardArtifact> newHits, boolean finalHitsStarted) {
        if (finalHitsStarted && (finalHits != null)) {
            finalHits.retainAll(newHits);
        } else {
            finalHits = newHits;
        }
    }

    /**
     * Test to see if the Email Object has any fields set that we don't support
     * right now.
     *
     * @return a list of unsupported fields found.
     */
    private String getListOfUnsupportedFields() {
        String fieldNames = "";
        if (obj.getHeader() != null) {
            if (obj.getHeader().getReceivedLines() != null) {
                fieldNames += "Received_Lines "; //NON-NLS
            }
            if (obj.getHeader().getBCC() != null) {
                fieldNames += "BCC "; //NON-NLS
            }
            if (obj.getHeader().getInReplyTo() != null) {
                fieldNames += "In_Reply_To "; //NON-NLS
            }
            if (obj.getHeader().getDate() != null) {
                fieldNames += "Date "; //NON-NLS
            }
            if (obj.getHeader().getMessageID() != null) {
                fieldNames += "Message_ID "; //NON-NLS
            }
            if (obj.getHeader().getSender() != null) {
                fieldNames += "Sender "; //NON-NLS
            }
            if (obj.getHeader().getReplyTo() != null) {
                fieldNames += "Reply_To "; //NON-NLS
            }
            if (obj.getHeader().getErrorsTo() != null) {
                fieldNames += "Errors_To "; //NON-NLS
            }
            if (obj.getHeader().getBoundary() != null) {
                fieldNames += "Boundary "; //NON-NLS
            }
            if (obj.getHeader().getContentType() != null) {
                fieldNames += "Content_Type "; //NON-NLS
            }
            if (obj.getHeader().getMIMEVersion() != null) {
                fieldNames += "MIME_Version "; //NON-NLS
            }
            if (obj.getHeader().getPrecedence() != null) {
                fieldNames += "Precedence "; //NON-NLS
            }
            if (obj.getHeader().getUserAgent() != null) {
                fieldNames += "User_Agent "; //NON-NLS
            }
            if (obj.getHeader().getXMailer() != null) {
                fieldNames += "X_Mailer "; //NON-NLS
            }
            if (obj.getHeader().getXOriginatingIP() != null) {
                fieldNames += "X_Originiating_IP "; //NON-NLS
            }
            if (obj.getHeader().getXPriority() != null) {
                fieldNames += "X_Priority "; //NON-NLS
            }

        }
        if (obj.getEmailServer() != null) {
            fieldNames += "Email_Server "; //NON-NLS
        }
        if (obj.getRawBody() != null) {
            fieldNames += "Raw_Body "; //NON-NLS
        }
        if (obj.getRawHeader() != null) {
            fieldNames += "Raw_Header "; //NON-NLS
        }
        if (obj.getAttachments() != null) {
            fieldNames += "Attachments "; //NON-NLS
        }
        if (obj.getLinks() != null) {
            fieldNames += "Links "; //NON-NLS
        }

        return fieldNames;
    }

}
