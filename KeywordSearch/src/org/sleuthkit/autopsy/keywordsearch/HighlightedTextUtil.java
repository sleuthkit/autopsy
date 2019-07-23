package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrDocumentList;

import java.util.Collection;

public class HighlightedTextUtil {

  static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
  static final String HIGHLIGHT_POST = "</span>"; //NON-NLS

  /**
   * If the Solr query does not produce valid highlighting, we attempt to add
   * the highlighting ourselves. We do this by taking the text returned from
   * the document that contains a hit and searching that text for the keyword
   * that produced the hit.
   *
   * @param solrDocumentList The list of Solr documents returned in response
   *                         to a Solr query. We expect there to only ever be
   *                         a single document.
   *
   * @return Either a string with the keyword highlighted via HTML span tags
   *         or a string indicating that we did not find a hit in the
   *         document.
   */
  static String attemptManualHighlighting(SolrDocumentList solrDocumentList, String highlightField, Collection<String> keywords) {
    if (solrDocumentList.isEmpty()) {
      return Bundle.IndexedText_errorMessage_errorGettingText();
    }

    // It doesn't make sense for there to be more than a single document in
    // the list since this class presents a single page (document) of highlighted
    // content at a time.  Hence we can just use get(0).
    String text = solrDocumentList.get(0).getOrDefault(highlightField, "").toString();

    // Escape any HTML content that may be in the text. This is needed in
    // order to correctly display the text in the content viewer.
    // Must be done before highlighting tags are added. If we were to
    // perform HTML escaping after adding the highlighting tags we would
    // not see highlighted text in the content viewer.
    text = StringEscapeUtils.escapeHtml(text);

    TreeRangeSet<Integer> highlights = TreeRangeSet.create();

    //for each keyword find the locations of hits and record them in the RangeSet
    for (String keyword : keywords) {
      //we also need to escape the keyword so that it matches the escaped text
      final String escapedKeyword = StringEscapeUtils.escapeHtml(keyword);
      int searchOffset = 0;
      int hitOffset = StringUtils.indexOfIgnoreCase(text, escapedKeyword, searchOffset);
      while (hitOffset != -1) {
        // Advance the search offset past the keyword.
        searchOffset = hitOffset + escapedKeyword.length();

        //record the location of the hit, possibly merging it with other hits
        highlights.add(Range.closedOpen(hitOffset, searchOffset));

        //look for next hit
        hitOffset = StringUtils.indexOfIgnoreCase(text, escapedKeyword, searchOffset);
      }
    }

    StringBuilder highlightedText = new StringBuilder(text);
    int totalHighLightLengthInserted = 0;
    //for each range to be highlighted...
    for (Range<Integer> highlightRange : highlights.asRanges()) {
      int hStart = highlightRange.lowerEndpoint();
      int hEnd = highlightRange.upperEndpoint();

      //insert the pre and post tag, adjusting indices for previously added tags
      highlightedText.insert(hStart + totalHighLightLengthInserted, HIGHLIGHT_PRE);
      totalHighLightLengthInserted += HIGHLIGHT_PRE.length();
      highlightedText.insert(hEnd + totalHighLightLengthInserted, HIGHLIGHT_POST);
      totalHighLightLengthInserted += HIGHLIGHT_POST.length();
    }

    return highlightedText.toString();
  }
}
