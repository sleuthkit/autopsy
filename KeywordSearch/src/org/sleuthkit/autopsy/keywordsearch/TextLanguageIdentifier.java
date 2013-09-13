/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author jmillman
 */
public interface TextLanguageIdentifier {

    /**
     * attempts to identify the language of the given String and add it to the black board for the given {@code AbstractFile}
     * as a TSK_TEXT_LANGUAGE attribute on a TSK_GEN_INFO artifact.
     *
     * @param extracted  the String whose language is to be identified
     * @param sourceFile the AbstractFile the string is extracted from.
     * @return
     */
  public  void addLanguageToBlackBoard(String extracted, AbstractFile sourceFile);
}
