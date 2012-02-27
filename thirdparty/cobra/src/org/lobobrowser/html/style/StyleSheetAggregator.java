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

package org.lobobrowser.html.style;

import java.net.MalformedURLException;
import java.util.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.HTMLDocumentImpl;
import org.lobobrowser.html.domimpl.HTMLElementImpl;
import org.w3c.dom.css.*;
import org.w3c.dom.stylesheets.*;

/**
 * Aggregates all style sheets in a document.
 * Every time a new STYLE element is found, it is
 * added to the style sheet aggreagator by means
 * of the {@link #addStyleSheet(CSSStyleSheet)} method.
 * HTML elements have a <code>style</code> object
 * that has a list of <code>CSSStyleDeclaration</code>
 * instances. The instances inserted in that list
 * are obtained by means of the {@link #getStyleDeclarations(HTMLElementImpl, String, String, String)}
 * method.
 */
public class StyleSheetAggregator {
	private final HTMLDocumentImpl document;
	private final Map classMapsByElement = new HashMap();
	private final Map idMapsByElement = new HashMap();
	private final Map rulesByElement = new HashMap();
	
	public StyleSheetAggregator(HTMLDocumentImpl document) {
		this.document = document;
	}
	
	public final void addStyleSheets(Collection styleSheets) throws MalformedURLException {
		Iterator i = styleSheets.iterator();
		while(i.hasNext()) {
			CSSStyleSheet sheet = (CSSStyleSheet) i.next();
			this.addStyleSheet(sheet);
		}
	}

	private final void addStyleSheet(CSSStyleSheet styleSheet) throws MalformedURLException {
		CSSRuleList ruleList = styleSheet.getCssRules();
		int length = ruleList.getLength();
		for(int i = 0; i < length; i++) {
			CSSRule rule = ruleList.item(i);
			this.addRule(styleSheet, rule);
		}
	}
	
	private final void addRule(CSSStyleSheet styleSheet, CSSRule rule) throws MalformedURLException {
		HTMLDocumentImpl document = this.document;
		if(rule instanceof CSSStyleRule) {
			CSSStyleRule sr = (CSSStyleRule) rule;
			String selectorList = sr.getSelectorText();
			StringTokenizer commaTok = new StringTokenizer(selectorList, ",");
			while(commaTok.hasMoreTokens()) {
				String selectorPart = commaTok.nextToken().toLowerCase();
				ArrayList simpleSelectors = null;
				String lastSelectorText = null;
				StringTokenizer tok = new StringTokenizer(selectorPart, " \t\r\n");
				if(tok.hasMoreTokens()) {
					simpleSelectors = new ArrayList();
					SimpleSelector prevSelector = null;
					SELECTOR_FOR:
					for(;;) {
						String token = tok.nextToken();
						if(">".equals(token)) {
							if(prevSelector != null) {
								prevSelector.setSelectorType(SimpleSelector.PARENT);
							}
							continue SELECTOR_FOR;
						}
						else if("+".equals(token)) {
							if(prevSelector != null) {
								prevSelector.setSelectorType(SimpleSelector.PRECEEDING_SIBLING);
							}
							continue SELECTOR_FOR;
						}
						int colonIdx = token.indexOf(':');
						String simpleSelectorText = colonIdx == -1 ? token : token.substring(0, colonIdx);
						String pseudoElement = colonIdx == -1 ? null : token.substring(colonIdx+1);
						prevSelector = new SimpleSelector(simpleSelectorText, pseudoElement);
						simpleSelectors.add(prevSelector);
						if(!tok.hasMoreTokens()) {
							lastSelectorText = simpleSelectorText;
							break;
						}
					}
				}
				if(lastSelectorText != null) {
					int dotIdx = lastSelectorText.indexOf('.');
					if(dotIdx != -1) {
						String elemtl = lastSelectorText.substring(0, dotIdx);
						String classtl = lastSelectorText.substring(dotIdx+1);
						this.addClassRule(elemtl, classtl, sr, simpleSelectors);
					}
					else {
						int poundIdx = lastSelectorText.indexOf('#');
						if(poundIdx != -1) {
							String elemtl = lastSelectorText.substring(0, poundIdx);
							String idtl = lastSelectorText.substring(poundIdx+1);
							this.addIdRule(elemtl, idtl, sr, simpleSelectors);
						}
						else {
							String elemtl = lastSelectorText;
							this.addElementRule(elemtl, sr, simpleSelectors);
						}
					}
				}
			}
			//TODO: Attribute selectors
		}
		else if(rule instanceof CSSImportRule) {
			UserAgentContext uacontext = document.getUserAgentContext();
			if(uacontext.isExternalCSSEnabled()) {
				CSSImportRule importRule = (CSSImportRule) rule;
				if(CSSUtilities.matchesMedia(importRule.getMedia(), uacontext)) {
					String href = importRule.getHref();
					String styleHref = styleSheet.getHref();
					String baseHref = styleHref == null ? document.getBaseURI() : styleHref;
					CSSStyleSheet sheet = CSSUtilities.parse(styleSheet.getOwnerNode(), href, document, baseHref, false);
					if(sheet != null) {
						this.addStyleSheet(sheet);
					}
				}
			}
		}
		else if(rule instanceof CSSMediaRule) {
			CSSMediaRule mrule = (CSSMediaRule) rule;
			MediaList mediaList = mrule.getMedia();
			if(CSSUtilities.matchesMedia(mediaList, document.getUserAgentContext())) {
				CSSRuleList ruleList = mrule.getCssRules();
				int length = ruleList.getLength();
				for(int i = 0; i < length; i++) {
					CSSRule subRule = ruleList.item(i);
					this.addRule(styleSheet, subRule);
				}
			}
		}		
	}

	private final void addClassRule(String elemtl, String classtl, CSSStyleRule styleRule, ArrayList ancestorSelectors) {
		Map classMap = (Map) this.classMapsByElement.get(elemtl);
		if(classMap == null) {
			classMap = new HashMap();
			this.classMapsByElement.put(elemtl, classMap);
		}
		Collection rules = (Collection) classMap.get(classtl);
		if(rules == null) {
			rules = new LinkedList();
			classMap.put(classtl, rules);
		}
		rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
	}
	
	private final void addIdRule(String elemtl, String idtl, CSSStyleRule styleRule, ArrayList ancestorSelectors) {
		Map idsMap = (Map) this.idMapsByElement.get(elemtl);
		if(idsMap == null) {
			idsMap = new HashMap();
			this.idMapsByElement.put(elemtl, idsMap);
		}
		Collection rules = (Collection) idsMap.get(idtl);
		if(rules == null) {
			rules = new LinkedList();
			idsMap.put(idtl, rules);
		}
		rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
	}
	
	private final void addElementRule(String elemtl, CSSStyleRule styleRule, ArrayList ancestorSelectors) {
		Collection rules = (Collection) this.rulesByElement.get(elemtl);
		if(rules == null) {
			rules = new LinkedList();
			this.rulesByElement.put(elemtl, rules);
		}
		rules.add(new StyleRuleInfo(ancestorSelectors, styleRule));
	}
		
	public final Collection getActiveStyleDeclarations(HTMLElementImpl element, String elementName, String elementId, String className, Set pseudoNames) {
		Collection styleDeclarations = null;
		String elementTL = elementName.toLowerCase();
		Collection elementRules = (Collection) this.rulesByElement.get(elementTL);
		if(elementRules != null) {
			Iterator i = elementRules.iterator();
			while(i.hasNext()) {
				StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
				if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
					CSSStyleRule styleRule = styleRuleInfo.styleRule;
					CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
					if(styleSheet != null && styleSheet.getDisabled()) {
						continue;
					}
					if(styleDeclarations == null) {
						styleDeclarations = new LinkedList();
					}
					styleDeclarations.add(styleRule.getStyle());
				}
				else {
				}
			}
		}
		elementRules = (Collection) this.rulesByElement.get("*");
		if(elementRules != null) {
			Iterator i = elementRules.iterator();
			while(i.hasNext()) {
				StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
				if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
					CSSStyleRule styleRule = styleRuleInfo.styleRule;
					CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
					if(styleSheet != null && styleSheet.getDisabled()) {
						continue;
					}
					if(styleDeclarations == null) {
						styleDeclarations = new LinkedList();
					}
					styleDeclarations.add(styleRule.getStyle());
				}
			}
		}
		if(className != null) {
			String classNameTL = className.toLowerCase();
			Map classMaps = (Map) this.classMapsByElement.get(elementTL);
			if(classMaps != null) {
				Collection classRules = (Collection) classMaps.get(classNameTL);
				if(classRules != null) {
					Iterator i = classRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
							CSSStyleRule styleRule = styleRuleInfo.styleRule;
							CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleDeclarations == null) {
								styleDeclarations = new LinkedList();
							}
							styleDeclarations.add(styleRule.getStyle());
						}
					}
				}
			}
			classMaps = (Map) this.classMapsByElement.get("*");
			if(classMaps != null) {
				Collection classRules = (Collection) classMaps.get(classNameTL);
				if(classRules != null) {
					Iterator i = classRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
							CSSStyleRule styleRule = styleRuleInfo.styleRule;
							CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleDeclarations == null) {
								styleDeclarations = new LinkedList();
							}
							styleDeclarations.add(styleRule.getStyle());
						}
					}
				}
			}
		}
		if(elementId != null) {
			Map idMaps = (Map) this.idMapsByElement.get(elementTL);
			if(idMaps != null) {
				String elementIdTL = elementId.toLowerCase();
				Collection idRules = (Collection) idMaps.get(elementIdTL);
				if(idRules != null) {
					Iterator i = idRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
							CSSStyleRule styleRule = styleRuleInfo.styleRule;
							CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleDeclarations == null) {
								styleDeclarations = new LinkedList();
							}
							styleDeclarations.add(styleRule.getStyle());
						}
					}				
				}
			}
			idMaps = (Map) this.idMapsByElement.get("*");
			if(idMaps != null) {
				String elementIdTL = elementId.toLowerCase();
				Collection idRules = (Collection) idMaps.get(elementIdTL);
				if(idRules != null) {
					Iterator i = idRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						if(styleRuleInfo.isSelectorMatch(element, pseudoNames)) {
							CSSStyleRule styleRule = styleRuleInfo.styleRule;
							CSSStyleSheet styleSheet = styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleDeclarations == null) {
								styleDeclarations = new LinkedList();
							}
							styleDeclarations.add(styleRule.getStyle());
						}
					}				
				}
			}
		}
		return styleDeclarations;
	}

	public final boolean affectedByPseudoNameInAncestor(HTMLElementImpl element, HTMLElementImpl ancestor, String elementName, String elementId, String[] classArray, String pseudoName) {
		String elementTL = elementName.toLowerCase();
		Collection elementRules = (Collection) this.rulesByElement.get(elementTL);
		if(elementRules != null) {
			Iterator i = elementRules.iterator();
			while(i.hasNext()) {
				StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
				CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
				if(styleSheet != null && styleSheet.getDisabled()) {
					continue;
				}
				if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
					return true;
				}
			}
		}
		elementRules = (Collection) this.rulesByElement.get("*");
		if(elementRules != null) {
			Iterator i = elementRules.iterator();
			while(i.hasNext()) {
				StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
				CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
				if(styleSheet != null && styleSheet.getDisabled()) {
					continue;
				}
				if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
					return true;
				}
			}
		}
		if(classArray != null) {
			for(int cidx  = 0; cidx < classArray.length; cidx++) {
				String className = classArray[cidx];
				String classNameTL = className.toLowerCase();
				Map classMaps = (Map) this.classMapsByElement.get(elementTL);
				if(classMaps != null) {
					Collection classRules = (Collection) classMaps.get(classNameTL);
					if(classRules != null) {
						Iterator i = classRules.iterator();
						while(i.hasNext()) {
							StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
							CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
								return true;
							}
						}
					}
				}
				classMaps = (Map) this.classMapsByElement.get("*");
				if(classMaps != null) {
					Collection classRules = (Collection) classMaps.get(classNameTL);
					if(classRules != null) {
						Iterator i = classRules.iterator();
						while(i.hasNext()) {
							StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
							CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
							if(styleSheet != null && styleSheet.getDisabled()) {
								continue;
							}
							if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
								return true;
							}
						}
					}
				}
			}
		}
		if(elementId != null) {
			Map idMaps = (Map) this.idMapsByElement.get(elementTL);
			if(idMaps != null) {
				String elementIdTL = elementId.toLowerCase();
				Collection idRules = (Collection) idMaps.get(elementIdTL);
				if(idRules != null) {
					Iterator i = idRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
						if(styleSheet != null && styleSheet.getDisabled()) {
							continue;
						}
						if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
							return true;
						}
					}				
				}
			}
			idMaps = (Map) this.idMapsByElement.get("*");
			if(idMaps != null) {
				String elementIdTL = elementId.toLowerCase();
				Collection idRules = (Collection) idMaps.get(elementIdTL);
				if(idRules != null) {
					Iterator i = idRules.iterator();
					while(i.hasNext()) {
						StyleRuleInfo styleRuleInfo = (StyleRuleInfo) i.next();
						CSSStyleSheet styleSheet = styleRuleInfo.styleRule.getParentStyleSheet();
						if(styleSheet != null && styleSheet.getDisabled()) {
							continue;
						}
						if(styleRuleInfo.affectedByPseudoNameInAncestor(element, ancestor, pseudoName)) {
							return true;
						}
					}				
				}
			}
		}
		return false;
	}

	private static class StyleRuleInfo {
		private final CSSStyleRule styleRule;
		private final ArrayList ancestorSelectors;

		/**
		 * @param selectors A collection of SimpleSelector's.
		 * @param rule A CSS rule.
		 */
		public StyleRuleInfo(ArrayList simpleSelectors, CSSStyleRule rule) {
			super();
			ancestorSelectors = simpleSelectors;
			styleRule = rule;
		}

		public final boolean affectedByPseudoNameInAncestor(HTMLElementImpl element, HTMLElementImpl ancestor, String pseudoName) {
			ArrayList as = this.ancestorSelectors;
			HTMLElementImpl currentElement = element;
			int size = as.size();
			boolean first = true;
			for(int i = size; --i >= 0;) {
				SimpleSelector simpleSelector = (SimpleSelector) as.get(i);
				if(first) {
					if(ancestor == element) {
						return simpleSelector.hasPseudoName(pseudoName);
					}
					first = false;
					continue;
				}
				String selectorText = simpleSelector.simpleSelectorText;
				int dotIdx = selectorText.indexOf('.');
				HTMLElementImpl newElement;
				if(dotIdx != -1) {
					String elemtl = selectorText.substring(0, dotIdx);
					String classtl = selectorText.substring(dotIdx+1);
					newElement = currentElement.getAncestorWithClass(elemtl, classtl);
				}
				else {
					int poundIdx = selectorText.indexOf('#');
					if(poundIdx != -1) {
						String elemtl = selectorText.substring(0, poundIdx);
						String idtl = selectorText.substring(poundIdx+1);
						newElement = currentElement.getAncestorWithId(elemtl, idtl);
					}
					else {
						String elemtl = selectorText;
						newElement = currentElement.getAncestor(elemtl);
					}
				}				
				if(newElement == null) {
					return false;
				}
				currentElement = newElement;					
				if(currentElement == ancestor) {
					return simpleSelector.hasPseudoName(pseudoName);
				}
			}
			return false;			
		}
		
		/**
		 * 
		 * @param element The element to test for a match.
		 * @param pseudoNames A set of pseudo-names in lowercase.
		 */
		private final boolean isSelectorMatch(HTMLElementImpl element, Set pseudoNames) {
			ArrayList as = this.ancestorSelectors;
			HTMLElementImpl currentElement = element;
			int size = as.size();
			boolean first = true;
			for(int i = size; --i >= 0;) {
				SimpleSelector simpleSelector = (SimpleSelector) as.get(i);
				if(first) {
					if(!simpleSelector.matches(pseudoNames)) {
						return false;
					}
					first = false;
					continue;
				}
				String selectorText = simpleSelector.simpleSelectorText;
				int dotIdx = selectorText.indexOf('.');
				int selectorType = simpleSelector.selectorType;
				HTMLElementImpl priorElement;
				if(dotIdx != -1) {
					String elemtl = selectorText.substring(0, dotIdx);
					String classtl = selectorText.substring(dotIdx+1);
					if(selectorType == SimpleSelector.ANCESTOR) {
						priorElement = currentElement.getAncestorWithClass(elemtl, classtl);
					}
					else if(selectorType == SimpleSelector.PARENT) {
						priorElement = currentElement.getParentWithClass(elemtl, classtl);
					}
					else if(selectorType == SimpleSelector.PRECEEDING_SIBLING) {
						priorElement = currentElement.getPreceedingSiblingWithClass(elemtl, classtl); 
					}
					else {
						throw new IllegalStateException("selectorType=" + selectorType);
					}
				}
				else {
					int poundIdx = selectorText.indexOf('#');
					if(poundIdx != -1) {
						String elemtl = selectorText.substring(0, poundIdx);
						String idtl = selectorText.substring(poundIdx+1);
						if(selectorType == SimpleSelector.ANCESTOR) {
							priorElement = currentElement.getAncestorWithId(elemtl, idtl);
						}
						else if(selectorType == SimpleSelector.PARENT) {
							priorElement = currentElement.getParentWithId(elemtl, idtl);
						}
						else if(selectorType == SimpleSelector.PRECEEDING_SIBLING) {
							priorElement = currentElement.getPreceedingSiblingWithId(elemtl, idtl); 
						}
						else {
							throw new IllegalStateException("selectorType=" + selectorType);
						}
					}
					else {
						String elemtl = selectorText;
						if(selectorType == SimpleSelector.ANCESTOR) {
							priorElement = currentElement.getAncestor(elemtl);
						}
						else if(selectorType == SimpleSelector.PARENT) {
							priorElement = currentElement.getParent(elemtl);
						}
						else if(selectorType == SimpleSelector.PRECEEDING_SIBLING) {
							priorElement = currentElement.getPreceedingSibling(elemtl); 
						}
						else {
							throw new IllegalStateException("selectorType=" + selectorType);
						}
					}
				}				
				if(priorElement == null) {
					return false;
				}
				if(!simpleSelector.matches(priorElement)) {
					return false;
				}
				currentElement = priorElement;					
			}
			return true;
		}
	}
	
	static class SimpleSelector {
		public static final int ANCESTOR = 0;
		public static final int PARENT = 1;
		public static final int PRECEEDING_SIBLING = 2;
		
		public final String simpleSelectorText;
		public final String pseudoElement;
		public int selectorType;

		/**
		 * 
		 * @param simpleSelectorText Simple selector text in lower case.
		 * @param pseudoElement The pseudo-element if any.
		 */
		public SimpleSelector(String simpleSelectorText, String pseudoElement) {
			super();
			this.simpleSelectorText = simpleSelectorText;
			this.pseudoElement = pseudoElement;
			this.selectorType = ANCESTOR;
		}
		
		public final boolean matches(HTMLElementImpl element) {
			Set names = element.getPseudoNames();
			if(names == null) {
				return this.pseudoElement == null;
			}
			else {
				String pe = this.pseudoElement;
				return pe == null || names.contains(pe);
			}
		}

		public final boolean matches(Set names) {
			if(names == null) {
				return this.pseudoElement == null;
			}
			else {
				String pe = this.pseudoElement;
				return pe == null || names.contains(pe);
			}
		}
		
		public final boolean matches(String pseudoName) {
			if(pseudoName == null) {
				return this.pseudoElement == null;
			}
			else {
				String pe = this.pseudoElement;
				return pe == null || pseudoName.equals(pe);
			}
		}
		
		public final boolean hasPseudoName(String pseudoName) {
			return pseudoName.equals(this.pseudoElement);
		}

		public int getSelectorType() {
			return selectorType;
		}

		public void setSelectorType(int selectorType) {
			this.selectorType = selectorType;
		}
	}
}
