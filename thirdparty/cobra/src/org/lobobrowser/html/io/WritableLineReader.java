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
 * Created on Nov 13, 2005
 */
package org.lobobrowser.html.io;

import java.io.*;

public class WritableLineReader extends LineNumberReader {
	private final Reader delegate;
	
	public WritableLineReader(Reader reader, int bufferSize) {
		super(reader, bufferSize);
		this.delegate = reader;
	}

	public WritableLineReader(Reader reader) {
		super(reader);
		this.delegate = reader;
	}

	/*
	 * Note: Not implicitly thread safe.
	 */
	public int read() throws IOException {
		StringBuffer sb = this.writeBuffer;
		if(sb != null && sb.length() > 0) {
			char ch = sb.charAt(0);
			sb.deleteCharAt(0);
			if(sb.length() == 0) {
				this.writeBuffer = null;
			}
			return (int) ch;
		}
		return super.read();
	}
	
	/* (non-Javadoc)
	 *  Note: Not implicitly thread safe.
	 * @see java.io.Reader#read(byte[], int, int)
	 */
	public int read(char[] b, int off, int len) throws IOException {
		StringBuffer sb = this.writeBuffer;
		if(sb != null && sb.length() > 0) {
			int srcEnd = Math.min(sb.length(), len);
			sb.getChars(0, srcEnd, b, off);
			sb.delete(0, srcEnd);
			if(sb.length() == 0) {
				this.writeBuffer = null;
			}
			return srcEnd;
		}
		return super.read(b, off, len);
	}	
	
	public boolean ready() throws IOException {
		StringBuffer sb = this.writeBuffer;
		if(sb != null && sb.length() > 0) {
			return true;
		}
		return super.ready();
	}

	/* (non-Javadoc)
	 * Note: Not implicitly thread safe.
	 * @see java.io.Reader#close()
	 */
	public void close() throws IOException {
		this.writeBuffer = null;
		super.close();
	}
	
	private StringBuffer writeBuffer = null;
	
	/**
	 * Note: Not implicitly thread safe.
	 * @param text
	 * @throws IOException
	 */
	public void write(String text) throws IOException {
		// Document overrides this to know that new data is coming.
		StringBuffer sb = this.writeBuffer;
		if(sb == null) {
			sb = new StringBuffer();
			this.writeBuffer = sb;
		}
		sb.append(text);
	}	
}
