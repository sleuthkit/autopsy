/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
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
package org.sleuthkit.autopsy.rejview;

import com.williballenthin.rejistry.RegistryParseException;
import com.williballenthin.rejistry.ValueData;
import java.awt.BorderLayout;
import java.io.UnsupportedEncodingException;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import org.openide.util.NbBundle.Messages;

public final class RejTreeValueView extends RejTreeNodeView {

    private static final long serialVersionUID = 1L;
    private final RejTreeValueNode _node;

    @Messages({"RejTreeValueView.template.name=Name:",
        "RejTreeValueView.template.type=Type:",
        "RejTreeValueView.failedToDecode.valueName=FAILED TO DECODE VALUE NAME",
        "RejTreeValueView.failedToDecode.valueType=FAILED TO PARSE VALUE TYPE",
        "RejTreeValueView.failedToParse.value=FAILED TO PARSE VALUE VALUE",
        "RejTreeValueView.metadataBorder.title=Metadata",
        "RejTreeValueView.valueBorder.title=Value",})
    public RejTreeValueView(RejTreeValueNode node) {
        super(new BorderLayout());
        this._node = node;

        /**
         * @param 1 Name
         * @param 2 Type
         */
        String metadataTemplate = "<html><i>"
                + Bundle.RejTreeValueView_template_name()
                + "</i><b>%1$s</b><br/><i>"
                + Bundle.RejTreeValueView_template_type()
                + "</i>   %2$s</html>";
        String valueName;
        String valueType;

        /**
         * @param 1 Value
         */
        String valueTemplate = ""
                + "<html>"
                + "%1$s"
                + "</html>";
        try {
            valueName = this._node.getValue().getName();
        } catch (UnsupportedEncodingException e) {
            valueName = Bundle.RejTreeValueView_failedToDecode_valueName();
        }

        try {
            valueType = this._node.getValue().getValueType().toString();
        } catch (RegistryParseException e) {
            valueType = Bundle.RejTreeValueView_failedToDecode_valueType();
        }

        JLabel metadataLabel = new JLabel(String.format(metadataTemplate, valueName, valueType), JLabel.LEFT);
        metadataLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_metadataBorder_title()));
        metadataLabel.setVerticalAlignment(SwingConstants.TOP);

        // this valueComponent must be set in the follow try/catch block.
        JComponent valueComponent;
        try {
            ValueData data = this._node.getValue().getValue();

            // the case statements are a bit repetitive, but i think make more sense than confusingly-nested if/elses
            switch (data.getValueType()) {
                case REG_SZ:
                case REG_EXPAND_SZ: {
                    String valueValue = data.getAsString();
                    JLabel valueLabel = new JLabel(String.format(valueTemplate, valueValue), JLabel.LEFT);
                    valueLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_valueBorder_title()));
                    valueLabel.setVerticalAlignment(SwingConstants.TOP);
                    valueComponent = valueLabel;
                    break;
                }
                case REG_MULTI_SZ: {
                    StringBuilder sb = new StringBuilder();
                    for (String s : data.getAsStringList()) {
                        sb.append(s);
                        sb.append("<br />");
                    }
                    String valueValue = sb.toString();
                    JLabel valueLabel = new JLabel(String.format(valueTemplate, valueValue), JLabel.LEFT);
                    valueLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_valueBorder_title()));
                    valueLabel.setVerticalAlignment(SwingConstants.TOP);
                    valueComponent = valueLabel;
                    break;
                }
                case REG_DWORD:
                case REG_QWORD:
                case REG_BIG_ENDIAN: {
                    String valueValue = String.format("0x%x", data.getAsNumber());
                    JLabel valueLabel = new JLabel(String.format(valueTemplate, valueValue), JLabel.LEFT);
                    valueLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_valueBorder_title()));
                    valueLabel.setVerticalAlignment(SwingConstants.TOP);
                    valueComponent = valueLabel;
                    break;
                }
                default: {
                    HexView hexView = new HexView(data.getAsRawData());
                    hexView.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_valueBorder_title()));
                    valueComponent = hexView;
                    break;
                }
            }
        } catch (RegistryParseException | UnsupportedEncodingException e) {
            JLabel valueLabel = new JLabel(String.format(valueTemplate, Bundle.RejTreeValueView_failedToParse_value()), JLabel.LEFT);
            valueLabel.setBorder(BorderFactory.createTitledBorder(Bundle.RejTreeValueView_valueBorder_title()));
            valueLabel.setVerticalAlignment(SwingConstants.TOP);
            valueComponent = valueLabel;
        }

        this.add(metadataLabel, BorderLayout.NORTH);
        this.add(new JScrollPane(valueComponent), BorderLayout.CENTER);
    }
}
