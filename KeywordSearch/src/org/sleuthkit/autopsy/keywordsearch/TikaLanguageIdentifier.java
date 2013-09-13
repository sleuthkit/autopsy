/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *TextLanguageIdentifier implementation based on a wrapped Tike LanguageIdentifier
 * 
 * 
 * @author jmillman
 */
public class TikaLanguageIdentifier implements TextLanguageIdentifier {

    private static final Logger logger = Logger.getLogger(TikaLanguageIdentifier.class.getName());

    @Override
    public void addLanguageToBlackBoard(String extracted, AbstractFile sourceFile) {

        org.apache.tika.language.LanguageIdentifier li = new org.apache.tika.language.LanguageIdentifier(extracted);
        
        logger.log(Level.INFO, sourceFile.getName() + " detected language: " + li.getLanguage()
                + "with " + ((li.isReasonablyCertain()) ? "HIGH" : "LOW") + "confidence");
        
        BlackboardArtifact genInfo;
        try {
            genInfo = sourceFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO);

            BlackboardAttribute textLang = new BlackboardAttribute(
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT_LANGUAGE.getTypeID(),
                    KeywordSearchIngestModule.MODULE_NAME, li.getLanguage());


            genInfo.addAttribute(textLang);

        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "failed to add TSK_TEXT_LANGUAGE attribute to TSK_GEN_INFO artifact for file: " + sourceFile.getName(), ex);
        }



              /*  //attempt to verify that artifact with attribute was created
                ArrayList<BlackboardArtifact> arts;
        
        
                try {
                    arts = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO, sourceFile.getId());
        
                    for (BlackboardArtifact art : arts) {
        
                        List<BlackboardAttribute> attrs = art.getAttributes();
                        for (BlackboardAttribute attr : attrs) {
                            if (attr.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT_LANGUAGE.getTypeID()) {
                                logger.log(Level.INFO, "succesfully added " + attr.getValueString() + " to gen info for:" + sourceFile.getName());
                                break;
                            }
                        }
                    }
                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                }*/
    }
}
