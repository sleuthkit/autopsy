/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * contact: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.geolocation;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicLabelUI;


/**
 * This class is an overload of BasicLabelUI to draw labels vertically.
 * 
 * This code was found at:
 * https://tech.chitgoks.com/2009/11/13/rotate-jlabel-vertically/
 * 
 */
final class VerticalLabelUI extends BasicLabelUI {
    
    private static final Rectangle paintIconR = new Rectangle();
    private static final Rectangle paintTextR = new Rectangle();
    private static final Rectangle paintViewR = new Rectangle();
    private static Insets paintViewInsets = new Insets(0, 0, 0, 0);

    static {
        labelUI = new VerticalLabelUI(false);
    }

    final boolean clockwise;

    /**
     * Construct a new VerticalLabelUI
     * @param clockwise 
     */
    VerticalLabelUI(boolean clockwise) {
        super();
        this.clockwise = clockwise;
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
    	Dimension dim = super.getPreferredSize(c);
    	return new Dimension( dim.height, dim.width );
    }
    
    @Override
    public void paint(Graphics g, JComponent c) {
        JLabel label = (JLabel)c;
        String text = label.getText();
        Icon icon = (label.isEnabled()) ? label.getIcon() : label.getDisabledIcon();

        if ((icon == null) && (text == null)) {
            return;
        }

        FontMetrics fm = g.getFontMetrics();
        paintViewInsets = c.getInsets(paintViewInsets);

        paintViewR.x = paintViewInsets.left;
        paintViewR.y = paintViewInsets.top;

    	// Use inverted height &amp; width
        paintViewR.height = c.getWidth() - (paintViewInsets.left + paintViewInsets.right);
        paintViewR.width = c.getHeight() - (paintViewInsets.top + paintViewInsets.bottom);

        paintIconR.x = paintIconR.y = paintIconR.width = paintIconR.height = 0;
        paintTextR.x = paintTextR.y = paintTextR.width = paintTextR.height = 0;

        String clippedText = layoutCL(label, fm, text, icon, paintViewR, paintIconR, paintTextR);

    	Graphics2D g2 = (Graphics2D) g;
    	AffineTransform tr = g2.getTransform();
    	if (clockwise) {
            g2.rotate( Math.PI / 2 );
            g2.translate( 0, - c.getWidth() );
    	} else {
            g2.rotate( - Math.PI / 2 );
            g2.translate( - c.getHeight(), 0 );
    	}

    	if (icon != null) {
            icon.paintIcon(c, g, paintIconR.x, paintIconR.y);
        }

        if (text != null) {
            int textX = paintTextR.x;
            int textY = paintTextR.y + fm.getAscent();

            if (label.isEnabled()) {
                paintEnabledText(label, g, clippedText, textX, textY);
            } else {
                paintDisabledText(label, g, clippedText, textX, textY);
            }
        }
	g2.setTransform( tr );
    }

}

