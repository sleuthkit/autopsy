package org.lobobrowser.html.domimpl;

import org.lobobrowser.html.*;
import org.lobobrowser.html.js.Executor;
import org.lobobrowser.js.*;
import org.mozilla.javascript.*;
import org.w3c.dom.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Implements common functionality of most elements.
 */
public class HTMLAbstractUIElement extends HTMLElementImpl {
	private Function onfocus, onblur, onclick, ondblclick, onmousedown, onmouseup, onmouseover, onmousemove, onmouseout, onkeypress, onkeydown, onkeyup, oncontextmenu;
	
	public HTMLAbstractUIElement(String name) {
		super(name);
	}

	public Function getOnblur() {
		return this.getEventFunction(onblur, "onblur");
	}

	public void setOnblur(Function onblur) {
		this.onblur = onblur;
	}

	public Function getOnclick() {
		return this.getEventFunction(onclick, "onclick");
	}

	public void setOnclick(Function onclick) {
		this.onclick = onclick;
	}

	public Function getOndblclick() {
		return this.getEventFunction(ondblclick, "ondblclick");
	}

	public void setOndblclick(Function ondblclick) {
		this.ondblclick = ondblclick;
	}

	public Function getOnfocus() {
		return this.getEventFunction(onfocus, "onfocus");
	}

	public void setOnfocus(Function onfocus) {
		this.onfocus = onfocus;
	}

	public Function getOnkeydown() {
		return this.getEventFunction(onkeydown, "onkeydown");
	}

	public void setOnkeydown(Function onkeydown) {
		this.onkeydown = onkeydown;
	}

	public Function getOnkeypress() {
		return this.getEventFunction(onkeypress, "onkeypress");
	}

	public void setOnkeypress(Function onkeypress) {
		this.onkeypress = onkeypress;
	}

	public Function getOnkeyup() {
		return this.getEventFunction(onkeyup, "onkeyup");
	}

	public void setOnkeyup(Function onkeyup) {
		this.onkeyup = onkeyup;
	}

	public Function getOnmousedown() {
		return this.getEventFunction(onmousedown, "onmousedown");
	}

	public void setOnmousedown(Function onmousedown) {
		this.onmousedown = onmousedown;
	}

	public Function getOnmousemove() {
		return this.getEventFunction(onmousemove, "onmousemove");
	}

	public void setOnmousemove(Function onmousemove) {
		this.onmousemove = onmousemove;
	}

	public Function getOnmouseout() {
		return this.getEventFunction(onmouseout, "onmouseout");
	}

	public void setOnmouseout(Function onmouseout) {
		this.onmouseout = onmouseout;
	}

	public Function getOnmouseover() {
		return this.getEventFunction(onmouseover, "onmouseover");
	}

	public void setOnmouseover(Function onmouseover) {
		this.onmouseover = onmouseover;
	}

	public Function getOnmouseup() {
		return this.getEventFunction(onmouseup, "onmouseup");
	}

	public void setOnmouseup(Function onmouseup) {
		this.onmouseup = onmouseup;
	}

	public Function getOncontextmenu() {
		return this.getEventFunction(oncontextmenu, "oncontextmenu");
	}

	public void setOncontextmenu(Function oncontextmenu) {
		this.oncontextmenu = oncontextmenu;
	}

	public void focus() {
		UINode node = this.getUINode();
		if(node != null) {
			node.focus();
		}
	}
	
	public void blur() {
		UINode node = this.getUINode();
		if(node != null) {
			node.blur();
		}		
	}
	
	private Map functionByAttribute = null;
	
	protected Function getEventFunction(Function varValue, String attributeName) {
		if(varValue != null) {
			return varValue;
		}
		String normalAttributeName = this.normalizeAttributeName(attributeName);
		synchronized(this) {
			Map fba = this.functionByAttribute;
			Function f = fba == null ? null : (Function) fba.get(normalAttributeName);
			if(f != null) {
				return f;
			}
			UserAgentContext uac = this.getUserAgentContext();
			if(uac == null) {
				throw new IllegalStateException("No user agent context.");
			}
			if(uac.isScriptingEnabled()) {
				String attributeValue = this.getAttribute(attributeName);
				if(attributeValue == null || attributeValue.length() == 0) {
					f = null;
				}
				else {
					String functionCode = "function " + normalAttributeName + "_" + System.identityHashCode(this) + "() { " + attributeValue + " }";
					Document doc = this.document;
					if(doc == null) {
						throw new IllegalStateException("Element does not belong to a document.");
					}
					Context ctx = Executor.createContext(this.getDocumentURL(), uac);
					try {
						Scriptable scope = (Scriptable) doc.getUserData(Executor.SCOPE_KEY);
						if(scope == null) {
							throw new IllegalStateException("Scriptable (scope) instance was expected to be keyed as UserData to document using " + Executor.SCOPE_KEY);
						}
						Scriptable thisScope = (Scriptable) JavaScript.getInstance().getJavascriptObject(this, scope);
						try {
							//TODO: Get right line number for script.					//TODO: Optimize this in case it's called multiple times? Is that done?
							f = ctx.compileFunction(thisScope, functionCode, this.getTagName() + "[" + this.getId() + "]." + attributeName, 1, null);
						} catch(EcmaError ecmaError) {
							logger.log(Level.WARNING, "Javascript error at " + ecmaError.getSourceName() + ":" + ecmaError.getLineNumber() + ": " + ecmaError.getMessage(), ecmaError);
							f = null;
						} catch(Throwable err) {
							logger.log(Level.WARNING, "Unable to evaluate Javascript code", err);
							f = null;
						}		
					} finally {
						Context.exit();
					}
				}
				if(fba == null) {
					fba = new HashMap(1);
					this.functionByAttribute = fba;
				}
				fba.put(normalAttributeName, f);
			}
			return f;			
		}
	}

	protected void assignAttributeField(String normalName, String value) {
		super.assignAttributeField(normalName, value);
		if(normalName.startsWith("on")) {
			synchronized(this) {
				Map fba = this.functionByAttribute;
				if(fba != null) {
					fba.remove(normalName);
				}
			}
		}
	}	
}
