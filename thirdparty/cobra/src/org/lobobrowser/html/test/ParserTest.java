package org.lobobrowser.html.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.io.*;
import java.util.logging.*;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;


import org.lobobrowser.html.*;
import org.lobobrowser.html.parser.*;
import org.lobobrowser.util.io.*;
import org.w3c.dom.*;

/**
 * Parser-only test frame.
 */
public class ParserTest extends JFrame {
	private static final Logger logger = Logger.getLogger(ParserTest.class.getName());
	private final JTree tree;
	private final JTextArea textArea;
	
	public ParserTest() throws HeadlessException {
		this("HTML Parser-Only Test Tool");
	}
	
	public ParserTest(String title) throws HeadlessException {
		super(title);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Container contentPane = this.getContentPane();
		contentPane.setLayout(new BorderLayout());
		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout());
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BorderLayout());
		final JTextField textField = new JTextField();
		JButton button = new JButton("Parse & Render");
		final JTabbedPane tabbedPane = new JTabbedPane();
		final JTree tree = new JTree();
		tree.setModel(null);
		final JScrollPane scrollPane = new JScrollPane(tree);
		
		this.tree = tree;
		
		contentPane.add(topPanel, BorderLayout.NORTH);
		contentPane.add(bottomPanel, BorderLayout.CENTER);
		
		topPanel.add(new JLabel("URL: "), BorderLayout.WEST);
		topPanel.add(textField, BorderLayout.CENTER);
		topPanel.add(button, BorderLayout.EAST);
		
		bottomPanel.add(tabbedPane, BorderLayout.CENTER);
				
		final JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		this.textArea = textArea;
		final JScrollPane textAreaSp = new JScrollPane(textArea);
		
		tabbedPane.addTab("HTML DOM", scrollPane);
		tabbedPane.addTab("Source Code", textAreaSp);
		
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent event) {
				process(textField.getText());
			}
		});
	}
	

	private void process(String uri) {
		try {
			URL url;
			try {
				url = new URL(uri);
			} catch(java.net.MalformedURLException mfu) {
				int idx = uri.indexOf(':');
				if(idx == -1 || idx == 1) {
					// try file
					url = new URL("file:" + uri);
				}
				else {
					throw mfu;
				}
			}
			logger.info("process(): Loading URI=[" + uri + "].");
			long time0 = System.currentTimeMillis();
			URLConnection connection = url.openConnection();
			connection.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible;) Cobra/0.96.1+");
			connection.setRequestProperty("Cookie", "");
			if(connection instanceof HttpURLConnection) {
				HttpURLConnection hc = (HttpURLConnection) connection;
				hc.setInstanceFollowRedirects(true);
				int responseCode = hc.getResponseCode();
				logger.info("process(): HTTP response code: " + responseCode);
			}
			InputStream in = connection.getInputStream();
			byte[] content;
			try {
				content = IORoutines.load(in, 8192);
			} finally {
				in.close();
			}
			String source = new String(content, "ISO-8859-1");
			this.textArea.setText(source);
			long time1 = System.currentTimeMillis();
			InputStream bin = new ByteArrayInputStream(content);
			UserAgentContext ucontext = new SimpleUserAgentContext();
			DocumentBuilderImpl builder = new DocumentBuilderImpl(ucontext);
			// Provide a proper URI, in case it was a file.
			String actualURI = url.toExternalForm();
			// Should change to use proper charset.
			Document document = builder.parse(new InputSourceImpl(bin, actualURI, "ISO-8859-1"));
			long time2 = System.currentTimeMillis();
			logger.info("Parsed URI=[" + uri + "]: Parse elapsed: " + (time2 - time1) + " ms. Load elapsed: " + (time1 - time0) + " ms.");
			this.tree.setModel(new NodeTreeModel(document));
		} catch(Exception err) {
			logger.log(Level.SEVERE, "Error trying to load URI=[" + uri + "].", err);
		}
	}

	public static void main(String[] args) {
		ParserTest frame = new ParserTest();
		frame.setSize(800, 400);
		frame.setExtendedState(TestFrame.MAXIMIZED_BOTH);
		frame.setVisible(true);		
	}
}
