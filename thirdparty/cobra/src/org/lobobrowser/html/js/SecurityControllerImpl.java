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

import org.mozilla.javascript.*;
import java.security.*;

public class SecurityControllerImpl extends SecurityController {
	private final java.net.URL url;
	private final java.security.Policy policy;
	private final CodeSource codesource;
	
	public SecurityControllerImpl(java.net.URL url, Policy policy) {
		this.url = url;
		this.policy = policy;
		this.codesource = new CodeSource(this.url, (java.security.cert.Certificate[]) null);
	}
	
	public Object callWithDomain(Object securityDomain, final Context ctx, final Callable callable, final Scriptable scope, final Scriptable thisObj, final Object[] args) {
		if(securityDomain == null) {
			return callable.call(ctx, scope, thisObj, args);
		}
		else {
			PrivilegedAction action = new PrivilegedAction() {
				public Object run() {
					return callable.call(ctx, scope, thisObj, args);					
				}
			};
			AccessControlContext acctx = new AccessControlContext(new ProtectionDomain[] { (ProtectionDomain) securityDomain });
			return AccessController.doPrivileged(action, acctx);
		}
	}

	public GeneratedClassLoader createClassLoader(ClassLoader parent, Object staticDomain) {
		return new LocalSecureClassLoader(parent);
	}

	public Object getDynamicSecurityDomain(Object securityDomain) {
		Policy policy = this.policy;
		if(policy == null) {
			return null;
		}
		else {
			PermissionCollection permissions = this.policy.getPermissions(codesource);
			return new ProtectionDomain(codesource, permissions);
		}
	}
	
	private class LocalSecureClassLoader extends SecureClassLoader implements GeneratedClassLoader {
		public LocalSecureClassLoader(ClassLoader parent) {
			super(parent);
		}

		public Class defineClass(String name, byte[] b) {
			return this.defineClass(name, b, 0, b.length, codesource);
		}

		public void linkClass(Class clazz) {
			super.resolveClass(clazz);
		}
	}
}
