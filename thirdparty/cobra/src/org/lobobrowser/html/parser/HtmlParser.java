/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The Lobo Project

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Contact info: lobochief@users.sourceforge.net
*/
/*
 * Created on Aug 28, 2005
 */
package org.lobobrowser.html.parser;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.DOMException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.lobobrowser.html.*;
import org.lobobrowser.html.io.*;

/**
 * The <code>HtmlParser</code> class is an HTML DOM parser.
 * This parser provides the functionality for
 * the standard DOM parser implementation {@link org.lobobrowser.html.parser.DocumentBuilderImpl}.
 * This parser class may be used directly when a different DOM
 * implementation is preferred.
 */
public class HtmlParser {
	private static final Logger logger = Logger.getLogger(HtmlParser.class.getName());
	private final Document document;
	private final UserAgentContext ucontext;
	private final ErrorHandler errorHandler;
	private final String publicId;
	private final String systemId;
	
	private static final Map ENTITIES = new HashMap(256);
	private static final Map ELEMENT_INFOS = new HashMap(35);

	/**
	 * A node <code>UserData</code> key used to tell
	 * nodes that their content may be about to be 
	 * modified. Elements could use this to temporarily
	 * suspend notifications. The value set 
	 * will be either <code>Boolean.TRUE</code> or
	 * <code>Boolean.FALSE</code>.
	 */
	public static final String MODIFYING_KEY = "cobra.suspend";
	
	static {
		Map entities = ENTITIES;
		entities.put("amp", new Character('&'));
		entities.put("lt", new Character('<'));
		entities.put("gt", new Character('>'));
		entities.put("quot", new Character('"'));
		entities.put("nbsp", new Character((char) 160));

		entities.put("lsquo", new Character('\u2018'));
		entities.put("rsquo", new Character('\u2019'));

		entities.put("frasl", new Character((char) 47));
		entities.put("ndash", new Character((char) 8211));
		entities.put("mdash", new Character((char) 8212));
		entities.put("iexcl", new Character((char) 161));
		entities.put("cent", new Character((char) 162));
		entities.put("pound", new Character((char) 163));
		entities.put("curren", new Character((char) 164));
		entities.put("yen", new Character((char) 165));
		entities.put("brvbar", new Character((char) 166));
		entities.put("brkbar", new Character((char) 166));
		entities.put("sect", new Character((char) 167));
		entities.put("uml", new Character((char) 168));
		entities.put("die", new Character((char) 168));
		entities.put("copy", new Character((char) 169));
		entities.put("ordf", new Character((char) 170));
		entities.put("laquo", new Character((char) 171));
		entities.put("not", new Character((char) 172));
		entities.put("shy", new Character((char) 173));
		entities.put("reg", new Character((char) 174));
		entities.put("macr", new Character((char) 175));
		entities.put("hibar", new Character((char) 175));
		entities.put("deg", new Character((char) 176));
		entities.put("plusmn", new Character((char) 177));
		entities.put("sup2", new Character((char) 178));
		entities.put("sup3", new Character((char) 179));
		entities.put("acute", new Character((char) 180));
		entities.put("micro", new Character((char) 181));
		entities.put("para", new Character((char) 182));
		entities.put("middot", new Character((char) 183));
		entities.put("cedil", new Character((char) 184));
		entities.put("sup1", new Character((char) 185));
		entities.put("ordm", new Character((char) 186));
		entities.put("raquo", new Character((char) 187));
		entities.put("frac14", new Character((char) 188));
		entities.put("frac12", new Character((char) 189));
		entities.put("frac34", new Character((char) 190));
		entities.put("iquest", new Character((char) 191));
		entities.put("Agrave", new Character((char) 192));
		entities.put("Aacute", new Character((char) 193));
		entities.put("Acirc", new Character((char) 194));
		entities.put("Atilde", new Character((char) 195));
		entities.put("Auml", new Character((char) 196));
		entities.put("Aring", new Character((char) 197));
		entities.put("AElig", new Character((char) 198));
		entities.put("Ccedil", new Character((char) 199));
		entities.put("Egrave", new Character((char) 200));
		entities.put("Eacute", new Character((char) 201));
		entities.put("Ecirc", new Character((char) 202));
		entities.put("Euml", new Character((char) 203));
		entities.put("Igrave", new Character((char) 204));
		entities.put("Iacute", new Character((char) 205));
		entities.put("Icirc", new Character((char) 206));
		entities.put("Iuml", new Character((char) 207));
		entities.put("ETH", new Character((char) 208));
		entities.put("Ntilde", new Character((char) 209));
		entities.put("Ograve", new Character((char) 210));
		entities.put("Oacute", new Character((char) 211));
		entities.put("Ocirc", new Character((char) 212));
		entities.put("Otilde", new Character((char) 213));
		entities.put("Ouml", new Character((char) 214));
		entities.put("times", new Character((char) 215));
		entities.put("Oslash", new Character((char) 216));
		entities.put("Ugrave", new Character((char) 217));
		entities.put("Uacute", new Character((char) 218));
		entities.put("Ucirc", new Character((char) 219));
		entities.put("Uuml", new Character((char) 220));
		entities.put("Yacute", new Character((char) 221));
		entities.put("THORN", new Character((char) 222));
		entities.put("szlig", new Character((char) 223));
		entities.put("agrave", new Character((char) 224));
		entities.put("aacute", new Character((char) 225));
		entities.put("acirc", new Character((char) 226));
		entities.put("atilde", new Character((char) 227));
		entities.put("auml", new Character((char) 228));
		entities.put("aring", new Character((char) 229));
		entities.put("aelig", new Character((char) 230));
		entities.put("ccedil", new Character((char) 231));
		entities.put("egrave", new Character((char) 232));
		entities.put("eacute", new Character((char) 233));
		entities.put("ecirc", new Character((char) 234));
		entities.put("euml", new Character((char) 235));
		entities.put("igrave", new Character((char) 236));
		entities.put("iacute", new Character((char) 237));
		entities.put("icirc", new Character((char) 238));
		entities.put("iuml", new Character((char) 239));
		entities.put("eth", new Character((char) 240));
		entities.put("ntilde", new Character((char) 241));
		entities.put("ograve", new Character((char) 242));
		entities.put("oacute", new Character((char) 243));
		entities.put("ocirc", new Character((char) 244));
		entities.put("otilde", new Character((char) 245));
		entities.put("ouml", new Character((char) 246));
		entities.put("divide", new Character((char) 247));
		entities.put("oslash", new Character((char) 248));
		entities.put("ugrave", new Character((char) 249));
		entities.put("uacute", new Character((char) 250));
		entities.put("ucirc", new Character((char) 251));
		entities.put("uuml", new Character((char) 252));
		entities.put("yacute", new Character((char) 253));
		entities.put("thorn", new Character((char) 254));
		entities.put("yuml", new Character((char) 255));

		//symbols from http://de.selfhtml.org/html/referenz/zeichen.htm
		
		//greek letters
		entities.put("Alpha", new Character((char) 913));
		entities.put("Beta", new Character((char) 914));
		entities.put("Gamma", new Character((char) 915));
		entities.put("Delta", new Character((char) 916));
		entities.put("Epsilon", new Character((char) 917));
		entities.put("Zeta", new Character((char) 918));
		entities.put("Eta", new Character((char) 919));
		entities.put("Theta", new Character((char) 920));
		entities.put("Iota", new Character((char) 921));
		entities.put("Kappa", new Character((char) 922));
		entities.put("Lambda", new Character((char) 923));
		entities.put("Mu", new Character((char) 924));
		entities.put("Nu", new Character((char) 925));
		entities.put("Xi", new Character((char) 926));
		entities.put("Omicron", new Character((char) 927));
		entities.put("Pi", new Character((char) 928));
		entities.put("Rho", new Character((char) 929));
		entities.put("Sigma", new Character((char) 930));
		entities.put("Sigmaf", new Character((char) 931));
		entities.put("Tau", new Character((char) 932));
		entities.put("Upsilon", new Character((char) 933));
		entities.put("Phi", new Character((char) 934));
		entities.put("Chi", new Character((char) 935));
		entities.put("Psi", new Character((char) 936));
		entities.put("Omega", new Character((char) 937));
		
		entities.put("alpha", new Character((char) 945));
		entities.put("beta", new Character((char) 946));
		entities.put("gamma", new Character((char) 947));
		entities.put("delta", new Character((char) 948));
		entities.put("epsilon", new Character((char) 949));
		entities.put("zeta", new Character((char) 950));
		entities.put("eta", new Character((char) 951));
		entities.put("theta", new Character((char) 952));
		entities.put("iota", new Character((char) 953));
		entities.put("kappa", new Character((char) 954));
		entities.put("lambda", new Character((char) 955));
		entities.put("mu", new Character((char) 956));
		entities.put("nu", new Character((char) 957));
		entities.put("xi", new Character((char) 958));
		entities.put("omicron", new Character((char) 959));
		entities.put("pi", new Character((char) 960));
		entities.put("rho", new Character((char) 961));
		entities.put("sigma", new Character((char) 962));
		entities.put("sigmaf", new Character((char) 963));
		entities.put("tau", new Character((char) 964));
		entities.put("upsilon", new Character((char) 965));
		entities.put("phi", new Character((char) 966));
		entities.put("chi", new Character((char) 967));
		entities.put("psi", new Character((char) 968));
		entities.put("omega", new Character((char) 969));
		entities.put("thetasym", new Character((char) 977));
		entities.put("upsih", new Character((char) 978));
		entities.put("piv", new Character((char) 982));
		
		//math symbols
		entities.put("forall", new Character((char) 8704));
		entities.put("part", new Character((char) 8706));
		entities.put("exist", new Character((char) 8707));
		entities.put("empty", new Character((char) 8709));
		entities.put("nabla", new Character((char) 8711));
		entities.put("isin", new Character((char) 8712));
		entities.put("notin", new Character((char) 8713));
		entities.put("ni", new Character((char) 8715));
		entities.put("prod", new Character((char) 8719));
		entities.put("sum", new Character((char) 8721));
		entities.put("minus", new Character((char) 8722));
		entities.put("lowast", new Character((char) 8727));
		entities.put("radic", new Character((char) 8730));
		entities.put("prop", new Character((char) 8733));
		entities.put("infin", new Character((char) 8734));
		entities.put("ang", new Character((char) 8736));
		entities.put("and", new Character((char) 8743));
		entities.put("or", new Character((char) 8744));
		entities.put("cap", new Character((char) 8745));
		entities.put("cup", new Character((char) 8746));
		entities.put("int", new Character((char) 8747));
		entities.put("there4", new Character((char) 8756));
		entities.put("sim", new Character((char) 8764));
		entities.put("cong", new Character((char) 8773));
		entities.put("asymp", new Character((char) 8776));
		entities.put("ne", new Character((char) 8800));
		entities.put("equiv", new Character((char) 8801));
		entities.put("le", new Character((char) 8804));
		entities.put("ge", new Character((char) 8805));
		entities.put("sub", new Character((char) 8834));
		entities.put("sup", new Character((char) 8835));
		entities.put("nsub", new Character((char) 8836));
		entities.put("sube", new Character((char) 8838));
		entities.put("supe", new Character((char) 8839));
		entities.put("oplus", new Character((char) 8853));
		entities.put("otimes", new Character((char) 8855));
		entities.put("perp", new Character((char) 8869));
		entities.put("sdot", new Character((char) 8901));
		entities.put("loz", new Character((char) 9674));
		
		//technical symbols
		entities.put("lceil", new Character((char) 8968));
		entities.put("rceil", new Character((char) 8969));
		entities.put("lfloor", new Character((char) 8970));
		entities.put("rfloor", new Character((char) 8971));
		entities.put("lang", new Character((char) 9001));
		entities.put("rang", new Character((char) 9002));
		
		//arrow symbols
		entities.put("larr", new Character((char) 8592));
		entities.put("uarr", new Character((char) 8593));
		entities.put("rarr", new Character((char) 8594));
		entities.put("darr", new Character((char) 8595));
		entities.put("harr", new Character((char) 8596));
		entities.put("crarr", new Character((char) 8629));
		entities.put("lArr", new Character((char) 8656));
		entities.put("uArr", new Character((char) 8657));
		entities.put("rArr", new Character((char) 8658));
		entities.put("dArr", new Character((char) 8659));
		entities.put("hArr", new Character((char) 8960));
		
		//divers symbols
		entities.put("bull", new Character((char) 8226));
		entities.put("prime", new Character((char) 8242));
		entities.put("Prime", new Character((char) 8243));
		entities.put("oline", new Character((char) 8254));
		entities.put("weierp", new Character((char) 8472));
		entities.put("image", new Character((char) 8465));
		entities.put("real", new Character((char) 8476));
		entities.put("trade", new Character((char) 8482));
		entities.put("euro", new Character((char) 8364));
		entities.put("alefsym", new Character((char) 8501));
		entities.put("spades", new Character((char) 9824));
		entities.put("clubs", new Character((char) 9827));
		entities.put("hearts", new Character((char) 9829));
		entities.put("diams", new Character((char) 9830));

		//ext lat symbols
		entities.put("OElig", new Character((char) 338));
		entities.put("oelig", new Character((char) 339));
		entities.put("Scaron", new Character((char) 352));
		entities.put("scaron", new Character((char) 353));
		entities.put("fnof", new Character((char) 402));

		//interpunction
		entities.put("ensp", new Character((char) 8194));
		entities.put("emsp", new Character((char) 8195));
		entities.put("thinsp", new Character((char) 8201));
		entities.put("zwnj", new Character((char) 8204));
		entities.put("zwj", new Character((char) 8205));
		entities.put("lrm", new Character((char) 8206));
		entities.put("rlm", new Character((char) 8207));
		
		entities.put("sbquo", new Character((char) 8218));
		entities.put("ldquo", new Character((char) 8220));
		entities.put("rdquo", new Character((char) 8221));
		entities.put("bdquo", new Character((char) 8222));
		entities.put("dagger", new Character((char) 8224));
		entities.put("Dagger", new Character((char) 8225));
		entities.put("hellip", new Character((char) 8230));
		entities.put("permil", new Character((char) 8240));
		entities.put("lsaquo", new Character((char) 8249));
		entities.put("rsaquo", new Character((char) 8250));
		
		//diacrit symb
		entities.put("circ", new Character((char) 710));
		entities.put("tilde", new Character((char) 732));
				
		Map elementInfos = ELEMENT_INFOS;
		
		elementInfos.put("NOSCRIPT", new ElementInfo(true, ElementInfo.END_ELEMENT_REQUIRED, null, true));
		
		ElementInfo optionalEndElement = new ElementInfo(true, ElementInfo.END_ELEMENT_OPTIONAL);
		ElementInfo forbiddenEndElement = new ElementInfo(false, ElementInfo.END_ELEMENT_FORBIDDEN);
		ElementInfo onlyTextDE = new ElementInfo(false, ElementInfo.END_ELEMENT_REQUIRED, true);
		ElementInfo onlyText = new ElementInfo(false, ElementInfo.END_ELEMENT_REQUIRED, false);
		
		Set tableCellStopElements = new HashSet();
		tableCellStopElements.add("TH");
		tableCellStopElements.add("TD");
		tableCellStopElements.add("TR");
		ElementInfo tableCellElement = new ElementInfo(true, ElementInfo.END_ELEMENT_OPTIONAL, tableCellStopElements);
		
		Set headStopElements = new HashSet();
		headStopElements.add("BODY");
		headStopElements.add("DIV");
		headStopElements.add("SPAN");
		headStopElements.add("TABLE");
		ElementInfo headElement = new ElementInfo(true, ElementInfo.END_ELEMENT_OPTIONAL, headStopElements);

		Set optionStopElements = new HashSet();
		optionStopElements.add("OPTION");
		optionStopElements.add("SELECT");
		ElementInfo optionElement = new ElementInfo(true, ElementInfo.END_ELEMENT_OPTIONAL, optionStopElements);
		
		Set paragraphStopElements = new HashSet();
		paragraphStopElements.add("P");
		paragraphStopElements.add("DIV");
		paragraphStopElements.add("TABLE");
		paragraphStopElements.add("PRE");
		paragraphStopElements.add("UL");
		paragraphStopElements.add("OL");
		ElementInfo paragraphElement = new ElementInfo(true, ElementInfo.END_ELEMENT_OPTIONAL, paragraphStopElements);

//		Set liStopElements = new HashSet();
//		liStopElements.add("LI");
//		liStopElements.add("UL");
//		liStopElements.add("OL");
		
		elementInfos.put("SCRIPT", onlyText);
		elementInfos.put("STYLE", onlyText);
		elementInfos.put("TEXTAREA", onlyTextDE);
		elementInfos.put("IMG", forbiddenEndElement);
		elementInfos.put("META", forbiddenEndElement);
		elementInfos.put("LINK", forbiddenEndElement);
		elementInfos.put("BASE", forbiddenEndElement);
		elementInfos.put("INPUT", forbiddenEndElement);
		elementInfos.put("FRAME", forbiddenEndElement);
		elementInfos.put("BR", forbiddenEndElement);
		elementInfos.put("HR", forbiddenEndElement);
		elementInfos.put("EMBED", forbiddenEndElement);
		elementInfos.put("SPACER", forbiddenEndElement);
		
		elementInfos.put("P", paragraphElement);
		elementInfos.put("LI", optionalEndElement);
		elementInfos.put("DT", optionalEndElement);
		elementInfos.put("DD", optionalEndElement);
		elementInfos.put("TR", optionalEndElement);
		elementInfos.put("TH", tableCellElement);
		elementInfos.put("TD", tableCellElement);
		elementInfos.put("HEAD", headElement);
		elementInfos.put("OPTION", optionElement);
		
		// Note: The specification states anchors have
		// a required end element, but browsers generally behave
		// as if it's optional.
		elementInfos.put("A", optionalEndElement);
		elementInfos.put("ANCHOR", optionalEndElement);
		//TODO: Keep adding tags here		
	}

	/**
	 * Constructs a <code>HtmlParser</code>.
	 * @param document A W3C Document instance.
	 * @param errorHandler The error handler. 
	 * @param publicId The public ID of the document.
	 * @param systemId The system ID of the document.
	 * @deprecated UserAgentContext should be passed in constructor.
	 */
	public HtmlParser(Document document, ErrorHandler errorHandler, String publicId, String systemId) {
		this.ucontext = null;
		this.document = document;
		this.errorHandler = errorHandler;
		this.publicId = publicId;
		this.systemId = systemId;
	}

	/**
	 * Constructs a <code>HtmlParser</code>.
	 * @param ucontext The user agent context.
	 * @param document An W3C Document instance.
	 * @param errorHandler The error handler. 
	 * @param publicId The public ID of the document.
	 * @param systemId The system ID of the document.
	 */
	public HtmlParser(UserAgentContext ucontext, Document document, ErrorHandler errorHandler, String publicId, String systemId) {
		this.ucontext = ucontext;
		this.document = document;
		this.errorHandler = errorHandler;
		this.publicId = publicId;
		this.systemId = systemId;
	}

	/**
	 * Constructs a <code>HtmlParser</code>.
	 * @param ucontext The user agent context.
	 * @param document A W3C Document instance.
	 */
	public HtmlParser(UserAgentContext ucontext, Document document) {
		this.ucontext = ucontext;
		this.document = document;
		this.errorHandler = null;
		this.publicId = null;
		this.systemId = null;
	}

	public static boolean isDecodeEntities(String elementName) {
		ElementInfo einfo = (ElementInfo) ELEMENT_INFOS.get(elementName.toUpperCase());
		return einfo == null ? true : einfo.decodeEntities;
	}
	
	/**
	 * Parses HTML from an input stream, assuming
	 * the character set is ISO-8859-1.
	 * @param in The input stream.
	 * @throws IOException Thrown when there are errors reading the stream.
	 * @throws SAXException Thrown when there are parse errors.
	 */
	public void parse(InputStream in) throws IOException,SAXException,UnsupportedEncodingException {
		this.parse(in, "ISO-8859-1");
	}
	
	/**
	 * Parses HTML from an input stream, using the given character set. 
	 * @param in The input stream.
	 * @param charset The character set.
	 * @throws IOException Thrown when there's an error reading from the stream.
	 * @throws SAXException Thrown when there is a parser error.
	 * @throws UnsupportedEncodingException Thrown if the character set is not supported.
	 */
	public void parse(InputStream in, String charset) throws IOException,SAXException,UnsupportedEncodingException {
		WritableLineReader reader = new WritableLineReader(new InputStreamReader(in, charset));
		this.parse(reader);
	}

	/**
	 * Parses HTML given by a <code>Reader</code>. This method appends
	 * nodes to the document provided to the parser.
	 * @param reader An instance of <code>Reader</code>.
	 * @throws IOException Thrown if there are errors reading the input stream.
	 * @throws SAXException Thrown if there are parse errors.
	 */
	public void parse(Reader reader) throws IOException, SAXException {
		this.parse(new LineNumberReader(reader));
	}

	public void parse(LineNumberReader reader) throws IOException, SAXException {
		Document doc = this.document;
		this.parse(reader, doc);
	}

	/**
	 * This method may be used when the DOM should be built under
	 * a given node, such as when <code>innerHTML</code> is used
	 * in Javascript.
	 * @param reader A document reader.
	 * @param parent The root node for the parsed DOM.
	 * @throws IOException
	 * @throws SAXException
	 */
	public void parse(Reader reader, Node parent) throws IOException, SAXException {
		this.parse(new LineNumberReader(reader), parent);
	}

	/**
	 * This method may be used when the DOM should be built under
	 * a given node, such as when <code>innerHTML</code> is used
	 * in Javascript.
	 * @param reader A LineNumberReader for the document.
	 * @param parent The root node for the parsed DOM.
	 * @throws IOException
	 * @throws SAXException
	 */
	public void parse(LineNumberReader reader, Node parent) throws IOException, SAXException {
		// Note: Parser does not clear document. It could be used incrementally.
		try {
			parent.setUserData(MODIFYING_KEY, Boolean.TRUE, null);
			try {
				while(this.parseToken(parent, reader, null, new LinkedList()) != TOKEN_EOD) {;}
			} catch(StopException se) {
				throw new SAXException("Unexpected flow exception", se);
			}
		} finally {
			parent.setUserData(MODIFYING_KEY, Boolean.FALSE, null);
		}
	}
		
	private static final int TOKEN_EOD = 0;
	private static final int TOKEN_COMMENT = 1;
	private static final int TOKEN_TEXT = 2;
	private static final int TOKEN_BEGIN_ELEMENT = 3;
	private static final int TOKEN_END_ELEMENT = 4;
	private static final int TOKEN_FULL_ELEMENT = 5;
	private static final int TOKEN_BAD = 6;
	
	private String normalLastTag = null;
	private boolean justReadTagBegin = false;
	private boolean justReadTagEnd = false;
	
	/**
	 * Only set when readAttribute returns false. 
	 */
	private boolean justReadEmptyElement = false;
	
	/**
	 * Parses text followed by one element.
	 * @param parent
	 * @param reader
	 * @param stopAtTagUC If this tag is encountered, the method throws StopException. 
	 * @param stopTags If tags in this set are encountered, the method throws StopException.
	 * @return
	 * @throws IOException
	 * @throws StopException
	 * @throws SAXException
	 */
	private final int parseToken(Node parent, LineNumberReader reader, Set stopTags, LinkedList ancestors) throws IOException, StopException, SAXException {
		Document doc = this.document;
		StringBuffer textSb = this.readUpToTagBegin(reader);
		if(textSb == null) {
			return TOKEN_EOD;
		}
		if(textSb.length() != 0) {
			//int textLine = reader.getLineNumber();
			StringBuffer decText = this.entityDecode(textSb);
			Node textNode = doc.createTextNode(decText.toString());
			try {
				parent.appendChild(textNode);
			} catch(DOMException de) {
				if(parent.getNodeType() != Node.DOCUMENT_NODE || de.code != DOMException.HIERARCHY_REQUEST_ERR) {
					logger.log(Level.WARNING, "parseToken(): Unable to append child to " + parent + ".", de);
				}
			}
		}
		if(this.justReadTagBegin) {
			String tag = this.readTag(parent, reader);
			if(tag == null) {
				return TOKEN_EOD;
			}
			String normalTag = tag.toUpperCase();
			try {
				if(tag.startsWith("!")) {
					if("!--".equals(tag)) {
						//int commentLine = reader.getLineNumber();
						StringBuffer comment = this.passEndOfComment(reader);
						StringBuffer decText = this.entityDecode(comment);
						parent.appendChild(doc.createComment(decText.toString()));
						return TOKEN_COMMENT;
					}
					else {
						//TODO: DOCTYPE node
						this.passEndOfTag(reader);
						return TOKEN_BAD;
					}
				}
				else if(tag.startsWith("/")) {
					tag = tag.substring(1);
					normalTag = normalTag.substring(1);
					this.passEndOfTag(reader);
					return TOKEN_END_ELEMENT;
				}
				else if (tag.startsWith("?")) {					
					tag = tag.substring(1);
					StringBuffer data = readProcessingInstruction(reader);					
					parent.appendChild(doc.createProcessingInstruction(tag, data.toString()));					
					return TOKEN_FULL_ELEMENT;
				}
				else {
					Element element = doc.createElement(tag);
					element.setUserData(MODIFYING_KEY, Boolean.TRUE, null);
					try {
						if(!this.justReadTagEnd) {
							while(this.readAttribute(reader, element)) {;}
						}
						if(stopTags != null && stopTags.contains(normalTag)) {
							// Throw before appending to parent.
							// After attributes are set.
							// After MODIFYING_KEY is set.
							throw new StopException(element);
						}
						// Add element to parent before children are added.
						// This is necessary for incremental rendering.
						parent.appendChild(element);
						if(!this.justReadEmptyElement) {
							ElementInfo einfo = (ElementInfo) ELEMENT_INFOS.get(normalTag);
							int endTagType = einfo == null ? ElementInfo.END_ELEMENT_REQUIRED : einfo.endElementType;
							if(endTagType != ElementInfo.END_ELEMENT_FORBIDDEN) {
								boolean childrenOk = einfo == null ? true : einfo.childElementOk;
								Set newStopSet = einfo == null ? null : einfo.stopTags;
								if(newStopSet == null) {
									if(endTagType == ElementInfo.END_ELEMENT_OPTIONAL) {
										newStopSet = Collections.singleton(normalTag);	
									}
								}
								if(stopTags != null) {
									if(newStopSet != null) {
										Set newStopSet2 = new HashSet();
										newStopSet2.addAll(stopTags);
										newStopSet2.addAll(newStopSet);
										newStopSet = newStopSet2;
									}
									else {
										newStopSet = endTagType == ElementInfo.END_ELEMENT_REQUIRED ? null : stopTags;
									}
								}
								ancestors.addFirst(normalTag);
								try {
									for(;;) {
										try {
											int token;
											if(einfo != null && einfo.noScriptElement) {
												UserAgentContext ucontext = this.ucontext;
												if(ucontext == null || ucontext.isScriptingEnabled()) {
													token = this.parseForEndTag(parent, reader, tag, false, einfo.decodeEntities);
												}
												else {
													token = this.parseToken(element, reader, newStopSet, ancestors);
												}
											}
											else {
												token = childrenOk ? this.parseToken(element, reader, newStopSet, ancestors) : this.parseForEndTag(element, reader, tag, true, einfo.decodeEntities);
											}
											if(token == TOKEN_END_ELEMENT) {
												String normalLastTag = this.normalLastTag;
												if(normalTag.equals(normalLastTag)) {
													return TOKEN_FULL_ELEMENT;
												}
												else  {
													ElementInfo closeTagInfo = (ElementInfo) ELEMENT_INFOS.get(normalLastTag);
													if(closeTagInfo == null || closeTagInfo.endElementType != ElementInfo.END_ELEMENT_FORBIDDEN) {
														//TODO: Rather inefficient algorithm, but it's probably executed infrequently?
														Iterator i = ancestors.iterator();
														if(i.hasNext()) {
															i.next();
															while(i.hasNext()) {
																String normalAncestorTag = (String) i.next();
																if(normalLastTag.equals(normalAncestorTag)) {
																	normalTag = normalLastTag;
																	return TOKEN_END_ELEMENT;																
																}
															}
														}
													}
													//TODO: Working here
												}
											}
											else if(token == TOKEN_EOD) {
												return TOKEN_EOD;
											}
										} catch(StopException se) {
											// newElement does not have a parent.
											Element newElement = se.getElement();
											tag = newElement.getTagName();
											normalTag = tag.toUpperCase();
											// If a subelement throws StopException with
											// a tag matching the current stop tag, the exception
											// is rethrown (e.g. <TR><TD>blah<TR><TD>blah)
											if(stopTags != null && stopTags.contains(normalTag)) {
												throw se;
											}
											einfo = (ElementInfo) ELEMENT_INFOS.get(normalTag);
											endTagType = einfo == null ? ElementInfo.END_ELEMENT_REQUIRED : einfo.endElementType;
											childrenOk = einfo == null ? true : einfo.childElementOk;
											newStopSet = einfo == null ? null : einfo.stopTags;
											if(newStopSet == null) {
												if(endTagType == ElementInfo.END_ELEMENT_OPTIONAL) {
													newStopSet = Collections.singleton(normalTag);	
												}
											}
											if(stopTags != null && newStopSet != null) {
												Set newStopSet2 = new HashSet();
												newStopSet2.addAll(stopTags);
												newStopSet2.addAll(newStopSet);
												newStopSet = newStopSet2;
											}
											ancestors.removeFirst();
											ancestors.addFirst(normalTag);
											//Switch element
											element.setUserData(MODIFYING_KEY, Boolean.FALSE, null);
											//newElement should have been suspended.
											element = newElement;
											// Add to parent
											parent.appendChild(element);
											if(this.justReadEmptyElement) {
												return TOKEN_BEGIN_ELEMENT;
											}
										}
									}
								} finally {
									ancestors.removeFirst();
								}
							}
						}
						return TOKEN_BEGIN_ELEMENT;
					} finally {
						// This can inform elements to continue with notifications.
						// It can also cause Javascript to get processed.
						element.setUserData(MODIFYING_KEY, Boolean.FALSE, null);
					}
				}
			} finally  {
				this.normalLastTag = normalTag;				
			}
		}
		else {
			this.normalLastTag = null;
			return TOKEN_TEXT;
		}
	}

	/**
	 * Reads text until the beginning of the next tag.
	 * Leaves the reader offset past the opening angle bracket.
	 * Returns null only on EOF.
	 */
	private final StringBuffer readUpToTagBegin(LineNumberReader reader) throws IOException, SAXException{
		StringBuffer sb = null;
		int intCh;
		while((intCh = reader.read()) != -1) {
			char ch = (char) intCh;
			if(ch == '<') {
				this.justReadTagBegin = true;
				this.justReadTagEnd = false;
				this.justReadEmptyElement = false;
				if(sb == null) {
					sb = new StringBuffer(0);
				}
				return sb;
			}
			if(sb == null) {
				sb = new StringBuffer();
			}
			sb.append(ch);
		}
		this.justReadTagBegin = false;
		this.justReadTagEnd = false;
		this.justReadEmptyElement = false;
		return sb;
	}

	/**
	 * Assumes that the content is completely made up of text,
	 * and parses until an ending tag is found.
	 * @param parent
	 * @param reader
	 * @param tagName
	 * @return
	 * @throws IOException
	 */
	private final int parseForEndTag(Node parent, LineNumberReader reader, String tagName, boolean addTextNode, boolean decodeEntities) throws IOException, SAXException {		
	    Document doc = this.document;
	    int intCh;
	    StringBuffer sb = new StringBuffer();
	    while((intCh = reader.read()) != -1) {
	    	char ch = (char) intCh;
	    	if(ch == '<') {
	    		intCh = reader.read();
	    		if(intCh != -1) {
	    			ch = (char) intCh;
	    			if(ch == '/') {
	    				StringBuffer tempBuffer = new StringBuffer();
	    				INNER:
	    				while((intCh = reader.read()) != -1) {
	    					ch = (char) intCh;
	    					if(ch == '>') {
	    						String thisTag = tempBuffer.toString().trim();
	    						if(thisTag.equalsIgnoreCase(tagName)) {
	    							this.justReadTagBegin = false;
	    							this.justReadTagEnd = true;
	    							this.justReadEmptyElement = false;
	    							this.normalLastTag = thisTag.toUpperCase();
	    							if(addTextNode) {
    									if(decodeEntities) {
    										sb = this.entityDecode(sb);
    									}
	    								String text = sb.toString();
	    								if(text.length() != 0) {
	    									Node textNode = doc.createTextNode(text);
	    									parent.appendChild(textNode);
	    								}
	    							}
	    							return HtmlParser.TOKEN_END_ELEMENT;
	    						}
	    						else {
	    							break INNER;
	    						}
	    					}
	    					else {
	    						tempBuffer.append(ch);
	    					}
	    				}
	    				sb.append("</");
	    				sb.append(tempBuffer);
	    			}
	    			else {
	    				sb.append('<');
	    			}
	    		}
	    	}
	    	sb.append(ch);
	    }
	    this.justReadTagBegin = false;
	    this.justReadTagEnd = false;
	    this.justReadEmptyElement = false;
	    if(addTextNode) {
			if(decodeEntities) {
				sb = this.entityDecode(sb);
			}
	    	String text = sb.toString();
	    	if(text.length() != 0) {
	    		Node textNode = doc.createTextNode(text);
	    		parent.appendChild(textNode);
	    	}
	    }
	    return HtmlParser.TOKEN_EOD;
	}
	
	    /**
	 * The reader offset should be 
	 * @param reader
	 * @return
	 */
	private final String readTag(Node parent, LineNumberReader reader) throws IOException {
		StringBuffer sb = new StringBuffer();
		int chInt;
		chInt = reader.read();
		if(chInt != -1) {
			boolean cont = true;
			char ch;
			LOOP:
			for(;;) {
				ch = (char) chInt;
				if(Character.isLetter(ch)) {
					// Speed up normal case
					break LOOP;
				}
				else if(ch == '!') {
					sb.append('!');
					chInt = reader.read();
					if(chInt != -1) {
						ch = (char) chInt;
						if(ch == '-') {
							sb.append('-');
							chInt = reader.read();
							if(chInt != -1) {
								ch = (char) chInt;
								if(ch == '-') {
									sb.append('-');
									cont = false;
								}
							}
							else {
								cont = false;
							}
						}
					}
					else {
						cont = false;
					}
				}
				else if(ch == '/') {
					sb.append(ch);
					chInt = reader.read();
					if(chInt != -1) {
						ch = (char) chInt;
					}
					else {
						cont = false;
					}
				}
				else if(ch == '<') {
					StringBuffer ltText = new StringBuffer(3);
					ltText.append('<');
					while((chInt = reader.read()) == '<') {
						ltText.append('<');
					}	
					Document doc = this.document;
					Node textNode = doc.createTextNode(ltText.toString());
					try {
						parent.appendChild(textNode);
					} catch(DOMException de) {
						if(parent.getNodeType() != Node.DOCUMENT_NODE || de.code != DOMException.HIERARCHY_REQUEST_ERR) {
							logger.log(Level.WARNING, "parseToken(): Unable to append child to " + parent + ".", de);
						}
					}			
					if(chInt == -1) {
						cont = false;
					}
					else {
						continue LOOP;
					}
				}
				else if(Character.isWhitespace(ch)) {
					StringBuffer ltText = new StringBuffer();
					ltText.append('<');
					ltText.append(ch);
					while((chInt = reader.read()) != -1) {
						ch = (char) chInt;
						if(ch == '<') {
							chInt = reader.read();
							break;
						}
						ltText.append(ch);
					}	
					Document doc = this.document;
					Node textNode = doc.createTextNode(ltText.toString());
					try {
						parent.appendChild(textNode);
					} catch(DOMException de) {
						if(parent.getNodeType() != Node.DOCUMENT_NODE || de.code != DOMException.HIERARCHY_REQUEST_ERR) {
							logger.log(Level.WARNING, "parseToken(): Unable to append child to " + parent + ".", de);
						}
					}			
					if(chInt == -1) {
						cont = false;
					}
					else {
						continue LOOP;
					}					
				}
				break LOOP;
			}
			if(cont) {
				boolean lastCharSlash = false;
				for(;;) {
					if(Character.isWhitespace(ch)) {
						break;
					}
					else if(ch == '>') {
						this.justReadTagEnd = true;
						this.justReadTagBegin = false;
						this.justReadEmptyElement = lastCharSlash;
						String tag = sb.toString();
						return tag;
					}
					else if (ch == '/') {
						lastCharSlash = true;
					}
					else {
						if(lastCharSlash) {
							sb.append('/');
						}
						lastCharSlash = false;
						sb.append(ch);
					}
					chInt = reader.read();
					if(chInt == -1) {
						break;
					}
					ch = (char) chInt;
				} 
			}
		}
		if(sb.length() > 0) {
			this.justReadTagEnd = false;
			this.justReadTagBegin = false;
			this.justReadEmptyElement = false;
		}
		String tag = sb.toString();
		return tag;
	}

	private final StringBuffer passEndOfComment(LineNumberReader reader) throws IOException {
		if(this.justReadTagEnd) {
			return new StringBuffer(0);
		}
		StringBuffer sb = new StringBuffer();
		OUTER:
		for(;;) {
			int chInt = reader.read();
			if(chInt == -1) {
				break OUTER;
			}
			char ch = (char) chInt;
			if(ch == '-') {
				chInt = reader.read();
				if(chInt == -1) {
					sb.append(ch);
					break OUTER;
				}
				ch = (char) chInt;
				if(ch == '-') {
					StringBuffer extra = null;
					INNER:
					for(;;) {
						chInt = reader.read();
						if(chInt == -1) {
							if(extra != null) {
								sb.append(extra.toString());
							}
							break OUTER; 
						}
						ch = (char) chInt;
						if(ch == '>') {
							this.justReadTagBegin = false;
							this.justReadTagEnd = true;
							return sb;
						}
						else if(ch == '-') {
							// Allow any number of dashes at the end
							if(extra == null) {
								extra = new StringBuffer();
								extra.append("--");
							}
							extra.append("-");
						}
						else if(Character.isWhitespace(ch)) {
							if(extra == null) {
								extra = new StringBuffer();
								extra.append("--");
							}
							extra.append(ch);
						}
						else {
							if(extra != null) {
								sb.append(extra.toString());
							}
							sb.append(ch);
							break INNER;
						}
					}
				}
				else {
					sb.append('-');
					sb.append(ch);
				}
			}
			else {
				sb.append(ch);
			}
		}
		if(sb.length() > 0) {
			this.justReadTagBegin = false;
			this.justReadTagEnd = false;
		}
		return sb; 
	}

	private final void passEndOfTag(Reader reader) throws IOException {
		if(this.justReadTagEnd) {
			return;
		}
		boolean readSomething = false; 
		for(;;) {
			int chInt = reader.read();
			if(chInt == -1) {
				break;
			}
			readSomething = true;
			char ch = (char) chInt;
			if(ch == '>') {
				this.justReadTagEnd = true;
				this.justReadTagBegin = false;
				return;
			}
		}
		if(readSomething) {
			this.justReadTagBegin = false;
			this.justReadTagEnd = false;
		}
	}
	
	private final StringBuffer readProcessingInstruction(LineNumberReader reader) throws IOException {
		StringBuffer pidata = new StringBuffer();		
		if(this.justReadTagEnd) {
			return pidata;
		}
		int ch;
		for(ch = reader.read();ch != -1 && ch != '>'; ch = reader.read())
		{			
			pidata.append((char)ch);			
		}		
		this.justReadTagBegin = false;
		this.justReadTagEnd = ch != -1;
		return pidata;
	}
	
	private final boolean readAttribute(LineNumberReader reader, Element element) throws IOException, SAXException {
		if(this.justReadTagEnd) {
			return false;
		}

		// Read attribute name up to '=' character.
		// May read several attribute names without explicit values.
		
		StringBuffer attributeName = null;
		boolean blankFound = false;
		boolean lastCharSlash = false;
		for(;;) {
			int chInt = reader.read();
			if(chInt == -1) {
				if(attributeName != null && attributeName.length() != 0) {
					String attributeNameStr = attributeName.toString();
					element.setAttribute(attributeNameStr, attributeNameStr);
					attributeName.setLength(0);
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = false;
				this.justReadEmptyElement = false;
				return false;
			}
			char ch = (char) chInt;
			if(ch == '=') {
				lastCharSlash = false;
				blankFound = false;
				break;
			}
			else if(ch == '>') {
				if(attributeName != null && attributeName.length() != 0) {
					String attributeNameStr = attributeName.toString();
					element.setAttribute(attributeNameStr, attributeNameStr);
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = true;
				this.justReadEmptyElement = lastCharSlash;
				return false;
			}
			else if(ch == '/') {
                blankFound = true;
                lastCharSlash = true;
			}
			else if(Character.isWhitespace(ch)) {
				lastCharSlash = false;
				blankFound = true;
			}
			else {
				lastCharSlash = false;
				if(blankFound) {
					blankFound = false;
					if(attributeName != null && attributeName.length() != 0) {
						String attributeNameStr = attributeName.toString();
						element.setAttribute(attributeNameStr, attributeNameStr);
						attributeName.setLength(0);
					}
				}
				if(attributeName == null) {
					attributeName = new StringBuffer(6);
				}
				attributeName.append(ch);
			}
		}
		// Read blanks up to open quote or first non-blank.
		StringBuffer attributeValue = null;
		int openQuote = -1;
		for(;;) {
			int chInt = reader.read();
			if(chInt == -1) {
				break;
			}
			char ch = (char) chInt;
			if(ch == '>') {
				if(attributeName != null && attributeName.length() != 0) {
					String attributeNameStr = attributeName.toString();
					element.setAttribute(attributeNameStr, attributeNameStr);
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = true;
				this.justReadEmptyElement = lastCharSlash;
				return false;
			}
			else if(ch == '/') {
                lastCharSlash = true;
			}
			else if(Character.isWhitespace(ch)) {
				lastCharSlash = false;
			}
			else {
				lastCharSlash = false;
				if(ch == '"') {
					openQuote = '"';
				}
				else if(ch == '\'') {
					openQuote = '\'';
				}
				else {
					openQuote = -1;
					if(attributeValue == null) {
						attributeValue = new StringBuffer(6);
					}
					attributeValue.append(ch);
				}
				break;
			}
		}

		// Read attribute value
		
		for(;;) {
			int chInt = reader.read();
			if(chInt == -1) {
				break;
			}
			char ch = (char) chInt;
			if(openQuote != -1 && ch == openQuote) {
				lastCharSlash = false;
				if(attributeName != null) {
					String attributeNameStr = attributeName.toString();
					if(attributeValue == null) {
						// Quotes are closed. There's a distinction
						// between blank values and null in HTML, as
						// processed by major browsers.
						element.setAttribute(attributeNameStr, "");
					}
					else {
						StringBuffer actualAttributeValue = this.entityDecode(attributeValue);
						element.setAttribute(attributeNameStr, actualAttributeValue.toString());
					}
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = false;
				return true;
			}
			else if(openQuote == -1 && ch == '>') {
				if(attributeName != null) {
					String attributeNameStr = attributeName.toString();
					if(attributeValue == null) {
						element.setAttribute(attributeNameStr, null);
					}
					else {
						StringBuffer actualAttributeValue = this.entityDecode(attributeValue);
						element.setAttribute(attributeNameStr, actualAttributeValue.toString());
					}
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = true;
				this.justReadEmptyElement = lastCharSlash;
				return false;
			}
			else if(openQuote == -1 && Character.isWhitespace(ch)) {
				lastCharSlash = false;
				if(attributeName != null) {
					String attributeNameStr = attributeName.toString();
					if(attributeValue == null) {
						element.setAttribute(attributeNameStr, null);
					}
					else {
						StringBuffer actualAttributeValue = this.entityDecode(attributeValue);
						element.setAttribute(attributeNameStr, actualAttributeValue.toString());
					}
				}
				this.justReadTagBegin = false;
				this.justReadTagEnd = false;
				return true;				
			}
			else {
				if(attributeValue == null) {
					attributeValue = new StringBuffer(6);
				}
				if(lastCharSlash) {
					attributeValue.append('/');
				}
				lastCharSlash = false;
				attributeValue.append(ch);
			}
		}
		this.justReadTagBegin = false;
		this.justReadTagEnd = false;
		if(attributeName != null) {
			String attributeNameStr = attributeName.toString();
			if(attributeValue == null) {
				element.setAttribute(attributeNameStr, null);
			}
			else {
				StringBuffer actualAttributeValue = this.entityDecode(attributeValue);
				element.setAttribute(attributeNameStr, actualAttributeValue.toString());
			}
		}
		return false;
	}
		
	private final StringBuffer entityDecode(StringBuffer rawText) throws org.xml.sax.SAXException {
		int startIdx = 0;
		StringBuffer sb = null;
		for(;;) {
			int ampIdx = rawText.indexOf("&", startIdx);
			if(ampIdx == -1) {
				if(sb == null) {
					return rawText;
				}
				else {
					sb.append(rawText.substring(startIdx));
					return sb;
				}
			}
			if(sb == null) {
				sb = new StringBuffer();
			}
			sb.append(rawText.substring(startIdx, ampIdx));
			int colonIdx = rawText.indexOf(";", ampIdx);
			if(colonIdx == -1) {
				sb.append('&');
				startIdx = ampIdx+1;
				continue;
			}
			String spec = rawText.substring(ampIdx+1, colonIdx);
			if(spec.startsWith("#")) {
				String number = spec.substring(1).toLowerCase();
				int decimal;
				try {
					if(number.startsWith("x")) {
						decimal = Integer.parseInt(number.substring(1), 16);
					}
					else {
						decimal = Integer.parseInt(number);
					}
				} catch(NumberFormatException nfe) {
					logger.log(Level.WARNING, "entityDecode()", nfe);
					decimal = 0;
				}
				sb.append((char) decimal);
			}
			else {
				int chInt = this.getEntityChar(spec);
				if(chInt == -1) {
					sb.append('&');
					sb.append(spec);
					sb.append(';');
				}
				else {
					sb.append((char) chInt);
				}
			}
			startIdx = colonIdx+1;
		}
	}
		
	private final Locator getLocator(int lineNumber, int columnNumber) {
		return new LocatorImpl(this.publicId, this.systemId, lineNumber, columnNumber);
	}
	
	private final int getEntityChar(String spec) {
		//TODO: Declared entities
		Character c = (Character) ENTITIES.get(spec);
		if(c == null) {
			String specTL = spec.toLowerCase();
			c = (Character) ENTITIES.get(specTL);
			if(c == null) {
				return -1;
			}
		}
		return (int) c.charValue();
	}
}
