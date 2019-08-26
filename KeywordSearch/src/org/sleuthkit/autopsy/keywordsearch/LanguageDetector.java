package org.sleuthkit.autopsy.keywordsearch;

import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class LanguageDetector {

  public enum Language {
    JAPANESE,
    ENGLISH,
  }

  private List<LanguageProfile> languageProfiles;

  LanguageDetector() {
    try {
      languageProfiles = Arrays.asList(
          new LanguageProfileReader().readBuiltIn(LdLocale.fromString("en")),
          new LanguageProfileReader().readBuiltIn(LdLocale.fromString("ja"))
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  Optional<Language> detect(String text) {
    com.optimaize.langdetect.LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
        .withProfiles(languageProfiles)
        .build();

    TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();

    TextObject textObject = textObjectFactory.forText(text);
    return languageDetector.detect(textObject).transform(Optional::of).or(Optional.empty()).map(LdLocale::getLanguage).flatMap(LanguageDetector::toLanguage);
  }

  private static Optional<Language> toLanguage(String s) {
    switch (s) {
      case "ja": return Optional.of(Language.JAPANESE);
      case "en": return Optional.of(Language.ENGLISH);
      default: return Optional.empty();
    }
  }
}
