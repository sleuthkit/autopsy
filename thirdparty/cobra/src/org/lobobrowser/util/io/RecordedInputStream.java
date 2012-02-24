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
 * Created on Apr 15, 2005
 */
package org.lobobrowser.util.io;

import java.io.*;

/**
 * Wraps an InputStream and records all of the
 * bytes read. This stream supports mark() and reset().
 * <p>
 * Note: Buffered streams should wrap this class
 * as opposed to the other way around.
 * @author J. H. S.
 */
public class RecordedInputStream extends InputStream {
	private final InputStream delegate;
	private final ByteArrayOutputStream store = new ByteArrayOutputStream();  
	private final int maxBufferSize;
	private boolean hasReachedEOF = false;
	private boolean hasReachedMaxBufferSize = false;
	private int markPosition = -1;
	private int readPosition = -1;
	private byte[] resetBuffer = null;
	
	/**
	 * 
	 */
	public RecordedInputStream(InputStream delegate, int maxBufferSize) {
		super();
		this.delegate = delegate;
		this.maxBufferSize = maxBufferSize;
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	public int read() throws IOException {
		if(this.readPosition != -1 && this.readPosition < this.resetBuffer.length) {
			int b = this.resetBuffer[this.readPosition];
			this.readPosition++;
			return b;
		}
		else {
			int b = this.delegate.read();
			if(b != -1) {
				if(!this.hasReachedMaxBufferSize) {
					this.store.write(b);
					if(this.store.size() > this.maxBufferSize) {
						this.hasReachedMaxBufferSize = true;
					}
				}	
			}
			else {
				this.hasReachedEOF = true;
			}
			return b;
		}
	}
	
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#available()
	 */
	public int available() throws IOException {
		return this.delegate.available();
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#close()
	 */
	public void close() throws IOException {
		this.delegate.close();
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#markSupported()
	 */
	public boolean markSupported() {
		return true;
	}
	
	public synchronized void mark(int readlimit) {
		if(this.hasReachedMaxBufferSize) {
			throw new java.lang.IllegalStateException("Maximum buffer size was already reached.");
		}
		this.markPosition = this.store.size();
	}

	public synchronized void reset() throws IOException {
		if(this.hasReachedMaxBufferSize) {
			throw new java.lang.IllegalStateException("Maximum buffer size was already reached.");
		}
		int mp = this.markPosition;
		byte[] wholeBuffer = this.store.toByteArray();
		byte[] resetBuffer = new byte[wholeBuffer.length - mp];
		System.arraycopy(wholeBuffer, mp, resetBuffer, 0, resetBuffer.length);
		this.resetBuffer = resetBuffer;
		this.readPosition = 0;
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	public int read(byte[] buffer, int offset, int length) throws IOException {
		if(this.readPosition != -1 && this.readPosition < this.resetBuffer.length) {
			int minLength = Math.min(this.resetBuffer.length - this.readPosition, length);
			System.arraycopy(this.resetBuffer, this.readPosition, buffer, offset, minLength);
			this.readPosition += minLength;
			return minLength;
		}
		else {
			int numRead = this.delegate.read(buffer, offset, length);
			if(numRead != -1) {
				if(!this.hasReachedMaxBufferSize) {
					this.store.write(buffer, offset, numRead);
					if(this.store.size() > this.maxBufferSize) {
						this.hasReachedMaxBufferSize = true;
					}
				}
			}
			else {
				this.hasReachedEOF = true;
			}
			return numRead;
		}
	}
	
	public void consumeToEOF() throws IOException {
		byte[] buffer = new byte[8192];
		while(this.read(buffer) != -1) {;}
	}
	
	public byte[] getBytesRead() throws BufferExceededException {
		if(this.hasReachedMaxBufferSize) {
			throw new BufferExceededException();
		}
		return this.store.toByteArray();
	}
	
	public String getString(String encoding) throws java.io.UnsupportedEncodingException, BufferExceededException {
		if(this.hasReachedMaxBufferSize) {
			throw new BufferExceededException();
		}
		byte[] bytes = this.store.toByteArray();
		return new String(bytes, encoding);
	}
	
	public boolean hasReachedEOF() {
		return this.hasReachedEOF;
	}
}
