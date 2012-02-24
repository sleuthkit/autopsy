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
 * Created on Nov 12, 2005
 */
package org.lobobrowser.html.js;

import java.util.*;
import java.lang.ref.*;
import javax.swing.Timer;
import java.awt.event.*;
import java.util.logging.*;

import org.lobobrowser.html.*;
import org.lobobrowser.html.domimpl.*;
import org.lobobrowser.js.*;
import org.lobobrowser.util.ID;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.w3c.dom.Document;
import org.w3c.dom.html2.*;
import org.w3c.dom.views.*;
import org.w3c.dom.css.*;

public class Window extends AbstractScriptableDelegate implements AbstractView  {
	private static final Logger logger = Logger.getLogger(Window.class.getName());
	private static final Map CONTEXT_WINDOWS = new WeakHashMap();
	//private static final JavaClassWrapper IMAGE_WRAPPER = JavaClassWrapperFactory.getInstance().getClassWrapper(Image.class);	
	private static final JavaClassWrapper XMLHTTPREQUEST_WRAPPER = JavaClassWrapperFactory.getInstance().getClassWrapper(XMLHttpRequest.class);	

	private static int timerIdCounter = 0;
	
	private final HtmlRendererContext rcontext;
	private final UserAgentContext uaContext;

	private Navigator navigator;
	private Screen screen;
	private Location location;
	private Map taskMap; 
	private volatile HTMLDocumentImpl document;
	
	public Window(HtmlRendererContext rcontext, UserAgentContext uaContext) {
		//TODO: Probably need to create a new Window instance
		//for every document. Sharing of Window state between
		//different documents is not correct.
		this.rcontext = rcontext;
		this.uaContext = uaContext;
	}

	private static int generateTimerID() {
	    synchronized(logger) {
	        return timerIdCounter++;
	    }
	}
	
	public HtmlRendererContext getHtmlRendererContext() {
		return this.rcontext;
	}
	
	public UserAgentContext getUserAgentContext() {
	    return this.uaContext;
	}
	
	private void clearState() {
		Scriptable s = this.getWindowScope();
		if(s != null) {
			Object[] ids = s.getIds();
			for(int i = 0; i < ids.length; i++) {
				Object id = ids[i];
				if(id instanceof String) {
					s.delete((String) id);
				}
				else if(id instanceof Integer) {
					s.delete(((Integer) id).intValue());
				}
			}
		}		
	}
	
	public void setDocument(HTMLDocumentImpl document) {
		Document prevDocument = this.document;
		if(prevDocument !=  document) {
			// Should clearing of the state be done
			// when window "unloads"?
			if(prevDocument != null) {
				// Only clearing when the previous document was not null
				// because state might have been set on the window before
				// the very first document is added.
				this.clearState();
			}
			this.initWindowScope(document);
			this.forgetAllTasks();
			Function onunload = this.onunload;
			if(onunload != null) {
				HTMLDocumentImpl oldDoc = (HTMLDocumentImpl) this.document;
				Executor.executeFunction(this.getWindowScope(), onunload, oldDoc.getDocumentURL(), this.uaContext);
				this.onunload = null;
			}
			this.document = document;
		}
	}

	public DocumentView getDocument() {
		return this.document;
	}

	public Document getDocumentNode() {
		return this.document;
	}
	
	private void putAndStartTask(Integer timeoutID, Timer timer, Object retained) {
		TaskWrapper oldTaskWrapper = null;
		synchronized(this) {
			Map taskMap = this.taskMap;
			if(taskMap == null) {
				taskMap = new HashMap(4);
				this.taskMap = taskMap;
			}
			else {
				oldTaskWrapper = (TaskWrapper) taskMap.get(timeoutID);
			}
			taskMap.put(timeoutID, new TaskWrapper(timer, retained));
		}	
		// Do this outside synchronized block, just in case.
		if(oldTaskWrapper != null) {
			oldTaskWrapper.timer.stop();
		}
		timer.start();
	}
	
	private void forgetTask(Integer timeoutID, boolean cancel) {
		TaskWrapper oldTimer = null;
		synchronized(this) {
			Map taskMap = this.taskMap;
			if(taskMap != null) {
				oldTimer = (TaskWrapper) taskMap.remove(timeoutID);
			}			
		}
		if(oldTimer != null && cancel) {
			oldTimer.timer.stop();
		}
	}
	
	private void forgetAllTasks() {
		TaskWrapper[] oldTaskWrappers = null;
		synchronized(this) {
			Map taskMap = this.taskMap;
			if(taskMap != null) {
				oldTaskWrappers = (TaskWrapper[]) taskMap.values().toArray(new TaskWrapper[0]);
				this.taskMap = null;
			}
		}
		if(oldTaskWrappers != null) {
			for(int i = 0; i < oldTaskWrappers.length; i++) {
				TaskWrapper taskWrapper = oldTaskWrappers[i];
				taskWrapper.timer.stop();
			}
		}
	}

	//	private Timer getTask(Long timeoutID) {
//		synchronized(this) {
//			Map taskMap = this.taskMap;
//			if(taskMap != null) {
//				return (Timer) taskMap.get(timeoutID);
//			}
//		}				
//		return null;
//	}
	
	
     /**
      * @param aFunction
      *            Javascript function to invoke on each loop.
      * @param aTimeInMs
      *            Time in millisecund between each loop.
      * @return Return the timer ID to use as reference
      * @see <a href="http://developer.mozilla.org/en/docs/DOM:window.setInterval">Window.setInterval interface
      *      definition</a>
      * @todo Make proper and refactore with {@link Window#setTimeout(Function, double)}.
      */
     public int setInterval(final Function aFunction, final double aTimeInMs)
     {
         if (aTimeInMs > Integer.MAX_VALUE || aTimeInMs < 0)
         {
             throw new IllegalArgumentException("Timeout value " + aTimeInMs + " is not supported.");
         }
         final int timeID = generateTimerID();
         final Integer timeIDInt = new Integer(timeID);
         ActionListener task = new FunctionTimerTask(this, timeIDInt, aFunction, false);
         int t = (int) aTimeInMs;
         if(t < 1) {
             t = 1;
         }
         Timer timer = new Timer(t, task);
         timer.setRepeats(true); // The only difference with setTimeout
         this.putAndStartTask(timeIDInt, timer, aFunction);
         return timeID;
     }
 
     /**
      * @param aExpression
      *            Javascript expression to invoke on each loop.
      * @param aTimeInMs
      *            Time in millisecund between each loop.
      * @return Return the timer ID to use as reference
      * @see <a href="http://developer.mozilla.org/en/docs/DOM:window.setInterval">Window.setInterval interface
      *      definition</a>
      * @todo Make proper and refactore with {@link Window#setTimeout(String, double)}.
      */
     public int setInterval(final String aExpression, double aTimeInMs)
     {
         if (aTimeInMs > Integer.MAX_VALUE || aTimeInMs < 0)
         {
             throw new IllegalArgumentException("Timeout value " + aTimeInMs + " is not supported.");
         }
    	 final int timeID = generateTimerID();
    	 final Integer timeIDInt = new Integer(timeID);
    	 ActionListener task = new ExpressionTimerTask(this, timeIDInt, aExpression, false); 
    	 int t = (int) aTimeInMs;
    	 if(t < 1) {
    	     t = 1;
    	 }
    	 Timer timer = new Timer(t, task);
    	 timer.setRepeats(false); // The only difference with setTimeout
    	 this.putAndStartTask(timeIDInt, timer, null);
    	 return timeID;
     }

     /**
      * @param aTimerID
      *          Timer ID to stop.
      * @see <a href="http://developer.mozilla.org/en/docs/DOM:window.clearInterval">Window.clearInterval interface Definition</a>
      */
     public void clearInterval(int aTimerID)
     {
    	 Integer key = new Integer(aTimerID);
    	 this.forgetTask(key, true);
     }

	public void alert(String message) {
		if(this.rcontext != null) {
			this.rcontext.alert(message);
		}
	}
	
	public void back() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.back();
		}
	}
	
	public void blur() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.blur();
		}
	}
	
	public void clearTimeout(int timeoutID) {
		Integer key = new Integer(timeoutID);
		this.forgetTask(key, true);
	}
	
	public void close() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.close();
		}
	}
	
	public boolean confirm(String message) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.confirm(message);
		}
		else {
			return false;
		}
	}
	
	public Object eval(String javascript) {
		HTMLDocumentImpl document = (HTMLDocumentImpl) this.document;
		if(document == null) {
			throw new IllegalStateException("Cannot evaluate if document is not set.");
		}
		Context ctx = Executor.createContext(document.getDocumentURL(), this.uaContext);
		try {
			Scriptable scope = this.getWindowScope();
			if(scope == null) {
				throw new IllegalStateException("Scriptable (scope) instance was expected to be keyed as UserData to document using " + Executor.SCOPE_KEY);
			}
			String scriptURI = "window.eval";
			if(logger.isLoggable(Level.INFO)) {
				logger.info("eval(): javascript follows...\r\n" + javascript);
			}
			return ctx.evaluateString(scope, javascript, scriptURI, 1, null);
		} finally {
			Context.exit();
		}
	}
	
	public void focus() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.focus();
		}
	}

	private void initWindowScope(final Document doc) {
		// Special Javascript class: XMLHttpRequest
		final Scriptable ws = this.getWindowScope();
		JavaInstantiator xi = new JavaInstantiator() {
			public Object newInstance() {
				Document d = doc;
				if(d == null) {
					throw new IllegalStateException("Cannot perform operation when document is unset.");
				}
				HTMLDocumentImpl hd;
				try {
					hd = (HTMLDocumentImpl) d; 
				} catch(ClassCastException err) {
					throw new IllegalStateException("Cannot perform operation with documents of type " + d.getClass().getName() + ".");
				}
				return new XMLHttpRequest(uaContext, hd.getDocumentURL(), ws);
			}
		};
		Function xmlHttpRequestC = JavaObjectWrapper.getConstructor("XMLHttpRequest", XMLHTTPREQUEST_WRAPPER, ws, xi);
		ScriptableObject.defineProperty(ws, "XMLHttpRequest", xmlHttpRequestC, ScriptableObject.READONLY);		

		// HTML element classes
		this.defineElementClass(ws, doc, "Image", "img", HTMLImageElementImpl.class);
		this.defineElementClass(ws, doc, "Script", "script", HTMLScriptElementImpl.class);
		this.defineElementClass(ws, doc, "IFrame", "iframe", HTMLIFrameElementImpl.class);
		this.defineElementClass(ws, doc, "Option", "option", HTMLOptionElementImpl.class);
		this.defineElementClass(ws, doc, "Select", "select", HTMLSelectElementImpl.class);
	}
	
	private ScriptableObject windowScope;

	public Scriptable getWindowScope() {
		synchronized(this) {
			ScriptableObject windowScope = this.windowScope;
			if(windowScope != null) {
				return windowScope;
			}
			// Context.enter() OK in this particular case.
			Context ctx = Context.enter();
			try {
				// Window scope needs to be top-most scope.
				windowScope = (ScriptableObject) JavaScript.getInstance().getJavascriptObject(this, null);
				ctx.initStandardObjects(windowScope);
				this.windowScope = windowScope;
				return windowScope;
			} finally {
				Context.exit();
			}
		}
	}
	
	private final void defineElementClass(Scriptable scope, final Document document, final String jsClassName, final String elementName, Class javaClass) {
		JavaInstantiator ji = new JavaInstantiator() {
			public Object newInstance() {
				Document d = document;
				if(d == null) {
					throw new IllegalStateException("Document not set in current context.");
				}
				return d.createElement(elementName);
			}
		};
		JavaClassWrapper classWrapper = JavaClassWrapperFactory.getInstance().getClassWrapper(javaClass);
		Function constructorFunction = JavaObjectWrapper.getConstructor(jsClassName, classWrapper, scope, ji);
		ScriptableObject.defineProperty(scope, jsClassName, constructorFunction, ScriptableObject.READONLY);		
	}
	
	public static Window getWindow(HtmlRendererContext rcontext) {
		if(rcontext == null) {
			return null;
		}
		synchronized(CONTEXT_WINDOWS) {
			Reference wref = (Reference) CONTEXT_WINDOWS.get(rcontext);
			if(wref != null) {
				Window window = (Window) wref.get();
				if(window != null) {
					return window;
				}
			}
			Window window = new Window(rcontext, rcontext.getUserAgentContext());
			CONTEXT_WINDOWS.put(rcontext, new WeakReference(window));
			return window;
		}
	}
	
	public Window open(String relativeUrl, String windowName, String windowFeatures, boolean replace) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			java.net.URL url;
			Object document = this.document;
			if(document instanceof HTMLDocumentImpl) {
				url = ((HTMLDocumentImpl) document).getFullURL(relativeUrl);
			}
			else {
				try {
					url = new java.net.URL(relativeUrl);
				} catch(java.net.MalformedURLException mfu) {
					throw new IllegalArgumentException("Malformed URI: " + relativeUrl);
				}
			}
			HtmlRendererContext newContext = rcontext.open(url, windowName, windowFeatures, replace);
			return getWindow(newContext);
		}
		else {
			return null;
		}
	}

	public Window open(String url) {
	    return this.open(url, "window:" + String.valueOf(ID.generateLong()));
	}

	public Window open(String url, String windowName) {
		return this.open(url, windowName, "", false);
	}

	public Window open(String url, String windowName, String windowFeatures) {
		return this.open(url, windowName, windowFeatures, false);
	}

	public String prompt(String message) {
		return this.prompt(message, "");
	}
	
	public String prompt(String message, int inputDefault) {
		return this.prompt(message, String.valueOf(inputDefault));
	}

	public String prompt(String message, String inputDefault) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.prompt(message, inputDefault);
		}
		else {
			return null;
		}
	}

	public void scrollTo(int x, int y) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.scroll(x, y);
		}
	}

	public void scrollBy(int x, int y) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.scrollBy(x, y);
		}
	}
	
	public void resizeTo(int width, int height) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.resizeTo(width, height);
		}		
	}
	
	public void resizeBy(int byWidth, int byHeight) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.resizeBy(byWidth, byHeight);
		}				
	}

	public int setTimeout(final String expr, double millis) {
        if(millis > Integer.MAX_VALUE || millis < 0) {
            throw new IllegalArgumentException("Timeout value " + millis + " is not supported.");
        }
		final int timeID = generateTimerID();
		final Integer timeIDInt = new Integer(timeID);
		ActionListener task = new ExpressionTimerTask(this, timeIDInt, expr, true); 
		int t = (int) millis;
		if(t < 1) {
		    t = 1;
		}
		Timer timer = new Timer(t, task);
		timer.setRepeats(false);
		this.putAndStartTask(timeIDInt, timer, null);
		return timeID;
	}
	
	public int setTimeout(final Function function, double millis) {
        if(millis > Integer.MAX_VALUE || millis < 0) {
            throw new IllegalArgumentException("Timeout value " + millis + " is not supported.");
        }
		final int timeID = generateTimerID();
		final Integer timeIDInt = new Integer(timeID);
		ActionListener task = new FunctionTimerTask(this, timeIDInt, function, true);
		int t = (int) millis;
		if(t < 1) {
		    t = 1;
		}
		Timer timer = new Timer(t, task);
		timer.setRepeats(false);
		this.putAndStartTask(timeIDInt, timer, function);
		return timeID;
	}

	public boolean isClosed() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.isClosed();
		}
		else {
			return false;
		}
	}
	
	public String getDefaultStatus() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.getDefaultStatus();
		}
		else {
			return null;
		}
	}
	
	public HTMLCollection getFrames() {
		Document doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getFrames();					
		}
		return null;
	}
	
	private int length;
	private boolean lengthSet = false;
	
	/**
	 * Gets the number of frames.
	 */
	public int getLength() {
		if(this.lengthSet) {
			return this.length;
		}
		else {
			HTMLCollection frames = this.getFrames();
			return frames == null ? 0 : frames.getLength();
		}
	}
	
	public void setLength(int length) {
		this.lengthSet = true;
		this.length = length;
	}
	
	public String getName() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.getName();
		}
		else {
			return null;
		}
	}
	
	public Window getParent() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return Window.getWindow(rcontext.getParent());
		}
		else {
			return null;
		}
	}
	
	public Window getOpener() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return Window.getWindow(rcontext.getOpener());
		}
		else {
			return null;
		}
	}
	
	public void setOpener(Window opener) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			if(opener == null) {
				rcontext.setOpener(null);
			}
			else {
				rcontext.setOpener(opener.rcontext);
			}
		}
	}
	
	public Window getSelf() {
		return this;
	}
	
	public String getStatus() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return rcontext.getStatus();
		}
		else {
			return null;
		}
	}
	
	public void setStatus(String message) {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			rcontext.setStatus(message);
		}
	}
		
	public Window getTop() {
		HtmlRendererContext rcontext = this.rcontext;
		if(rcontext != null) {
			return Window.getWindow(rcontext.getTop());
		}
		else {
			return null;
		}
	}
	
	public Window getWindow() {
		return this;
	}
	
	public Navigator getNavigator() {
		synchronized(this) {
			Navigator nav = this.navigator;
			if(nav == null) {
				nav = new Navigator(this.uaContext);
				this.navigator = nav;
			}
			return nav;
		}
	}
	
	public Screen getScreen() {
		synchronized(this) {
			Screen nav = this.screen;
			if(nav == null) {
				nav = new Screen();
				this.screen = nav;
			}
			return nav;
		}		
	}
	
	public Location getLocation() {
		synchronized(this) {
			Location location = this.location;
			if(location == null) {
				location = new Location(this);
				this.location = location;
			}
			return location;
		}
	}
	
	public void setLocation(String location) {
		this.getLocation().setHref(location);
	}
	
	private History history; 
	
	public History getHistory() {
        synchronized(this) {
            History history = this.history;
            if(history == null) {
                history = new History(this);
                this.history = history;
            }
            return history;
        }	    
	}
	
	public CSS2Properties getComputedStyle(HTMLElement element, String pseudoElement) {
		if(element instanceof HTMLElementImpl) {
			return ((HTMLElementImpl) element).getComputedStyle(pseudoElement);
		}
		else {
			throw new java.lang.IllegalArgumentException("Element implementation unknown: " + element);
		}
	}
	
	public Function getOnload() {
		Document doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			return ((HTMLDocumentImpl) doc).getOnloadHandler();
		}
		else {
			return null;
		}
	}

	public void setOnload(Function onload) {
		//Note that body.onload overrides
		//window.onload.
		Document doc = this.document;
		if(doc instanceof HTMLDocumentImpl) {
			((HTMLDocumentImpl) doc).setOnloadHandler(onload);
		}
	}

	private Function onunload;

	public Function getOnunload() {
		return onunload;
	}

	public void setOnunload(Function onunload) {
		this.onunload = onunload;
	}
	
	
	public org.w3c.dom.Node namedItem(String name) {
		// Bug 1928758: Element IDs are named objects in context. 
		HTMLDocumentImpl doc = this.document;
		if(doc == null) {
			return null;
		}
		org.w3c.dom.Node node = doc.getElementById(name);
		if(node != null) {
			return node;
		}
		return null;
	}
	
	public void forceGC() {
	    System.gc();
	}
	
	private static abstract class WeakWindowTask implements ActionListener {
	    private final WeakReference windowRef;

	    public WeakWindowTask(Window window) {
	        this.windowRef = new WeakReference(window);
	    }
	    
	    protected Window getWindow() {
	        WeakReference ref = this.windowRef;
	        return ref == null ? null : (Window) ref.get();
	    }	    
	}
	
	private static class FunctionTimerTask extends WeakWindowTask {
	    // Implemented as a static WeakWindowTask to allow the Window
	    // to get garbage collected, especially in infinite loop 
	    // scenarios.
	    private final Integer timeIDInt;
	    private final WeakReference functionRef;
	    private final boolean removeTask;
	    
	    public FunctionTimerTask(Window window, Integer timeIDInt, Function function, boolean removeTask) {
	        super(window);
	        this.timeIDInt = timeIDInt;
	        this.functionRef = new WeakReference(function);
	        this.removeTask = removeTask;
	    }
	    
	    public void actionPerformed(ActionEvent e) {
	        // This executes in the GUI thread and that's good.
	        try {
	            Window window = this.getWindow(); 
	            if(window == null) {
	                if(logger.isLoggable(Level.INFO)) {
	                    logger.info("actionPerformed(): Window is no longer available.");
	                }
	                return;
	            }
	            if(this.removeTask) {
	                window.forgetTask(this.timeIDInt, false);
	            }
	            HTMLDocumentImpl doc = (HTMLDocumentImpl) window.getDocument();
	            if(doc == null) {
	                throw new IllegalStateException("Cannot perform operation when document is unset.");
	            }
	            Function function = (Function) this.functionRef.get();
	            if(function == null) {
	                throw new IllegalStateException("Cannot perform operation. Function is no longer available.");
	            }
	            Executor.executeFunction(window.getWindowScope(), function, doc.getDocumentURL(), window.getUserAgentContext());
	        } catch(Throwable err) {
	            logger.log(Level.WARNING, "actionPerformed()", err);
	        }
	    }           
	}
	
	private static class ExpressionTimerTask extends WeakWindowTask {
	    // Implemented as a static WeakWindowTask to allow the Window
	    // to get garbage collected, especially in infinite loop 
	    // scenarios.
	    private final Integer timeIDInt;
	    private final String expression;
	    private final boolean removeTask;

	    public ExpressionTimerTask(Window window, Integer timeIDInt, String expression, boolean removeTask) {
	        super(window);
	        this.timeIDInt = timeIDInt;
	        this.expression = expression;
	        this.removeTask = removeTask;
	    }

	    public void actionPerformed(ActionEvent e) {
	        // This executes in the GUI thread and that's good.
	        try {
	            Window window = this.getWindow(); 
	            if(window == null) {
                    if(logger.isLoggable(Level.INFO)) {
                        logger.info("actionPerformed(): Window is no longer available.");
                    }
	                return;
	            }
	            if(this.removeTask) {
	                window.forgetTask(this.timeIDInt, false);
	            }
	            HTMLDocumentImpl doc = (HTMLDocumentImpl) window.getDocument();
	            if(doc == null) {
	                throw new IllegalStateException("Cannot perform operation when document is unset.");
	            }
	            window.eval(this.expression);
	        } catch(Throwable err) {
	            logger.log(Level.WARNING, "actionPerformed()", err);
	        }
	    }           
	}

	private static class TaskWrapper {
	    public final Timer timer;
	    private final Object retained;
	    
        public TaskWrapper(Timer timer, Object retained) {
            super();
            this.timer = timer;
            this.retained = retained;
        }
	}
	
}

