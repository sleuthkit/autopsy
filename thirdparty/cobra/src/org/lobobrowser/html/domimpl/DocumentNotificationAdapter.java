/*
    GNU LESSER GENERAL PUBLIC LICENSE
    Copyright (C) 2006 The XAMJ Project

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
package org.lobobrowser.html.domimpl;

/**
 * An abstract implementation of {@link DocumentNotificationListener}
 * with blank methods, provided for convenience.
 */
public abstract class DocumentNotificationAdapter implements
		DocumentNotificationListener {
	public void allInvalidated() {
	}

	public void externalScriptLoading(NodeImpl node) {
	}
	
	public void invalidated(NodeImpl node) {
	}

	public void lookInvalidated(NodeImpl node) {
	}

	public void nodeLoaded(NodeImpl node) {
	}

	public void positionInvalidated(NodeImpl node) {
	}

	public void sizeInvalidated(NodeImpl node) {
	}

	public void structureInvalidated(NodeImpl node) {
	}
}
