/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation.translators;

import com.google.cloud.translate.Language;

/**
 *
 * @author wschaefer
 */
class LanguageWrapper {
    private final Language language;
    
    LanguageWrapper(Language lang){
        language = lang;
    }
    
    Language getLanguage(){
        return language;
    }
    
    @Override
    public String toString(){
      return language.getName();
    }
        
}
