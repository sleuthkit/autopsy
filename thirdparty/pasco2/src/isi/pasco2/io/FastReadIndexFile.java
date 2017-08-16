/*
 * Copyright 2006 Bradley Schatz. All rights reserved.
 *
 * This file is part of pasco2, the next generation Internet Explorer cache
 * and history record parser.
 *
 * pasco2 is free software; you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * pasco2 is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with pasco2; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */


package isi.pasco2.io;

import isi.pasco2.util.StructConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FastReadIndexFile implements IndexFile {
  ByteBuffer content = null;

  public FastReadIndexFile(String fileName) throws FileNotFoundException,
      IOException {
    File file = new File(fileName);

    // Create a read-only memory-mapped file
    FileChannel roChannel = new RandomAccessFile(file, "r").getChannel();
    ByteBuffer roBuf = roChannel.map(FileChannel.MapMode.READ_ONLY, 0,
        (int) roChannel.size());

    byte[] buf = new byte[roBuf.limit()];
    roBuf.get(buf, 0, roBuf.limit());
    content = ByteBuffer.wrap(buf);
    roChannel.close();
  }

  public FastReadIndexFile(String fileName, InputStream stream, long len) throws FileNotFoundException,
      IOException {

    ByteBuffer roBuf = ByteBuffer.allocate((int)len);
    byte[] buf = roBuf.array();
    
    int offset = 0;
    int res = 0;
    while ((res = stream.read(buf, offset, (int)len-offset)) > 0) {
      offset = offset + res;  
      if (offset == len) {
        break;
      }
    }
    stream.close();
    content = roBuf;
  }

  public FastReadIndexFile(String fileName, String mode)
      throws FileNotFoundException, IOException {
    this(fileName);
  }

  public void seek(long offset) throws IOException {
    content.clear();
    content.position((int) offset);

  }

  public int readLittleEndianInt() throws IOException {
    byte[] bytes = new byte[4];
    content.get(bytes, 0, 4);
    return StructConverter.bytesIntoInt(bytes, 0);
  }

  public long readLittleEndianUInt() throws IOException {
    byte[] bytes = new byte[4];
    content.get(bytes, 0, 4);
    return StructConverter.bytesIntoUInt(bytes, 0);
  }

  public int readLittleEndianShort() throws IOException {
    byte[] bytes = new byte[2];
    content.get(bytes, 0, 2);
    return StructConverter.bytesIntoShort(bytes, 0);
  }

  public int read(byte[] buf, int offset, int count) throws IOException {
    content.get(buf, offset, count);
    return count;
  }

  public int read() throws IOException {
    return content.get();
  }

  public char readAsciiChar() throws IOException {
    return (char) content.get();
  }

  public int readUnsignedByte() throws IOException {
    return content.get();
  }

  public void close() throws IOException {

  }

  public String readStringAtOffset(long offset, int length) throws IOException {
    seek(offset);
    byte[] dn = new byte[length];
    read(dn, 0, length);
    if (dn[0] == 0) {
      return new String();
    } else {
      return new String(dn);
    }
  }

  public int readIntAtOffset(long offset) throws IOException {
    seek(offset);
    return readLittleEndianInt();
  }

  public long readUIntAtOffset(long offset) throws IOException {
    seek(offset);
    return readLittleEndianUInt();
  }

  public int readShortAtOffset(long offset) throws IOException {
    seek(offset);
    return readLittleEndianShort();
  }
}
