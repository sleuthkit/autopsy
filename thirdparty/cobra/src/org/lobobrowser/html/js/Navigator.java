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
import org.lobobrowser.js.*;

public class Navigator extends AbstractScriptableDelegate {
	private final UserAgentContext context;

	/**
	 * @param context
	 */
	Navigator(UserAgentContext context) {
		super();
		this.context = context;
	}
		
	public String getAppCodeName() {
		return this.context.getAppCodeName();
	}

	public String getAppName() {
		return this.context.getAppName();
	}
	
	public String getAppVersion() {
		return this.context.getAppVersion();
	}

	public String getAppMinorVersion() {
		return this.context.getAppMinorVersion();
	}
	
	public String getPlatform() {
		return this.context.getPlatform();
	}
	
	public String getUserAgent() {
		return this.context.getUserAgent();
	}	
	
	public String getVendor() {
		return this.context.getVendor();
	}
	
	public String getProduct() {
		return this.context.getProduct();
	}
	
	public boolean javaEnabled() {
		// True always?
		return true;
	}
	
	private MimeTypesCollection mimeTypes;
	
	public MimeTypesCollection getMimeTypes() {
		synchronized(this) {
			MimeTypesCollection mt = this.mimeTypes;
			if(mt == null) {
				mt = new MimeTypesCollection();
				this.mimeTypes = mt;
			}
			return mt;
		}
	}

	public class MimeTypesCollection  {
		// Class must be public to allow JavaScript access
		public int getLength() {
			return 0;
		}

		public Object item(int index) {
			return null;
		}

		public Object namedItem(String name) {
			return null;
		}
	}
}