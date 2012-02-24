package org.lobobrowser.html.renderer;

import java.awt.*;

class BrokenComponent extends Component {
	public Dimension getPreferredSize() {
		return new Dimension(10, 10);
	}
	
	public void update(Graphics g) {
		this.paint(g);
	}
	
	public void paint(Graphics g) {
		g.setColor(Color.RED);
		Dimension size = this.getSize();
		g.drawRect(0, 0, size.width, size.height);
		g.drawLine(0, 0, size.width - 1, size.height - 1);
		g.drawLine(size.width - 1, 0, 0, size.height - 1);
	}
}
