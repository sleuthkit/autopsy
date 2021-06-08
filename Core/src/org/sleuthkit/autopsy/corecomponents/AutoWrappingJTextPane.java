/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.text.MessageFormat;
import javax.swing.JTextPane;
import javax.swing.SizeRequirements;
import javax.swing.text.Element;
import javax.swing.text.View;
import static javax.swing.text.View.GoodBreakWeight;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import javax.swing.text.html.ParagraphView;
import javax.swing.text.html.StyleSheet;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerDefaults;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;

/**
 * JTextPane extension that auto wraps input text using an HTMLEditorKit trick.
 */
public class AutoWrappingJTextPane extends JTextPane {

    public AutoWrappingJTextPane() {
        /*
         * This appears to be an attempt to modify the wrapping behavior of the
         * text pane. Taken form this website: http://java-sl.com/tip_html_letter_wrap.html.
         */
        HTMLEditorKit editorKit = new HTMLEditorKit() {
            private static final long serialVersionUID = 1L;

            @Override
            public ViewFactory getViewFactory() {

                return new HTMLEditorKit.HTMLFactory() {
                    @Override
                    public View create(Element e) {
                        View v = super.create(e);
                        if (v instanceof InlineView) {
                            return new InlineView(e) {
                                @Override
                                public int getBreakWeight(int axis, float pos, float len) {
                                    return GoodBreakWeight;
                                }

                                @Override
                                public View breakView(int axis, int p0, float pos, float len) {
                                    if (axis == View.X_AXIS) {
                                        checkPainter();
                                        int p1 = getGlyphPainter().getBoundedPosition(this, p0, pos, len);
                                        if (p0 == getStartOffset() && p1 == getEndOffset()) {
                                            return this;
                                        }
                                        return createFragment(p0, p1);
                                    }
                                    return this;
                                }
                            };
                        } else if (v instanceof ParagraphView) {
                            return new ParagraphView(e) {
                                @Override
                                protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
                                    SizeRequirements requirements = r;
                                    if (requirements == null) {
                                        requirements = new SizeRequirements();
                                    }
                                    float pref = layoutPool.getPreferredSpan(axis);
                                    float min = layoutPool.getMinimumSpan(axis);
                                    // Don't include insets, Box.getXXXSpan will include them. 
                                    requirements.minimum = (int) min;
                                    requirements.preferred = Math.max(requirements.minimum, (int) pref);
                                    requirements.maximum = Integer.MAX_VALUE;
                                    requirements.alignment = 0.5f;
                                    return requirements;
                                }
                            };
                        }
                        return v;
                    }
                };
            }
        };

        this.setEditorKit(editorKit);
    }
    
    

    @Override
    public void setText(String text) {
        // setting the text format with style to avoid problems with overridden styles.
        String style = String.format("font-family: %s; font-size: %dpt; margin: 0px; padding: 0px 0px %dpx 0px;",
                    ContentViewerDefaults.getFont().getFamily(), ContentViewerDefaults.getFont().getSize(), ContentViewerDefaults.getLineSpacing());
        
        super.setText(MessageFormat.format("<pre style=\"{0}\">{1}</pre>", style, EscapeUtil.escapeHtml(text)));
    }
}
