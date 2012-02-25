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
 * Created on Jun 19, 2005
 */
package org.lobobrowser.util;

import java.security.SecureClassLoader;

/**
 * Base class for all project class loaders.
 * @author J. H. S.
 */
public abstract class BaseClassLoader extends SecureClassLoader {

	/**
	 * @param parent
	 */
	public BaseClassLoader(ClassLoader parent) {
		super(parent);
	}

	/**
	 * 
	 */
	public BaseClassLoader() {
		super();
	}

	/* (non-Javadoc)
	 * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
	 */
	public synchronized Class loadClass(String name, boolean resolve)
			throws ClassNotFoundException {
		return super.loadClass(name, resolve);
	}	
}
