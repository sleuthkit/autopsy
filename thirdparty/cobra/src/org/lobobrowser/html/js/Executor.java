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
package org.lobobrowser.html.js;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.js.JavaScript;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import java.util.logging.*;

public class Executor {
	private static final Logger logger = Logger.getLogger(Executor.class.getName());

	/**
	 * This method should be invoked instead of <code>Context.enter</code>.
	 * @param codeSource
	 * @param ucontext
	 */
	public static Context createContext(java.net.URL codeSource, UserAgentContext ucontext) {
		Context prev = Context.getCurrentContext();
		Context ctx = Context.enter();
		ctx.setOptimizationLevel(ucontext.getScriptingOptimizationLevel());
		if(prev == null) {
			// If there was a previous context, this one must be nested.
			// We still need to create a context because of exit() but
			// we cannot set a new security controller.
			ctx.setSecurityController(new SecurityControllerImpl(codeSource, ucontext.getSecurityPolicy()));
		}
		return ctx;
	}
	
	public static boolean executeFunction(NodeImpl element, Function f, Event event) {
		return Executor.executeFunction(element, element, f, event);
	}
	
	public static boolean executeFunction(NodeImpl element, Object thisObject, Function f, Event event) {
		Document doc = element.getOwnerDocument();
		if(doc == null) {
			throw new IllegalStateException("Element does not belong to a document.");
		}
		Context ctx = createContext(element.getDocumentURL(), element.getUserAgentContext());
		try {
			Scriptable scope = (Scriptable) doc.getUserData(Executor.SCOPE_KEY);
			if(scope == null) {
				throw new IllegalStateException("Scriptable (scope) instance was expected to be keyed as UserData to document using " + Executor.SCOPE_KEY);
			}
			JavaScript js = JavaScript.getInstance();
			Scriptable thisScope = (Scriptable) js.getJavascriptObject(thisObject, scope);
			try {
				Scriptable eventScriptable = (Scriptable) js.getJavascriptObject(event, thisScope);
				ScriptableObject.defineProperty(thisScope, "event", eventScriptable, ScriptableObject.READONLY);
				Object result = f.call(ctx, thisScope, thisScope, new Object[0]);
				if(!(result instanceof Boolean)) {
					return true;
				}
				return ((Boolean) result).booleanValue();
			} catch(Throwable thrown) {
				logger.log(Level.WARNING, "executeFunction(): There was an error in Javascript code.", thrown);
				return true;
			}
		} finally {
			Context.exit();
		}					
	}

	public static boolean executeFunction(Scriptable thisScope, Function f, java.net.URL codeSource, UserAgentContext ucontext) {
		Context ctx = createContext(codeSource, ucontext);
		try {
			try {
				Object result = f.call(ctx, thisScope, thisScope, new Object[0]);
				if(!(result instanceof Boolean)) {
					return true;
				}
				return ((Boolean) result).booleanValue();
			} catch(Throwable err) {
				logger.log(Level.WARNING, "executeFunction(): Unable to execute Javascript function " + f.getClassName() + ".", err);
				return true;
			}
		} finally {
			Context.exit();
		}					
	}

	/**
	 * A document <code>UserData</code> key used
	 * to map Javascript scope in the HTML document.
	 */
	public static final String SCOPE_KEY = "cobra.js.scope";
}
