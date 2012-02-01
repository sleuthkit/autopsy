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
import java.io.RandomAccessFile;

public class EnhancedRandomAccessFile extends RandomAccessFile implements
    IndexFile {
  public EnhancedRandomAccessFile(File arg0, String arg1)
      throws FileNotFoundException {
    super(arg0, arg1);
  }

  public EnhancedRandomAccessFile(String arg0, String arg1)
      throws FileNotFoundException {
    super(arg0, arg1);
  }

  public int readLittleEndianInt() throws IOException {
    byte[] bytes = new byte[4];
    read(bytes, 0, 4);
    return StructConverter.bytesIntoInt(bytes, 0);
  }

  public long readLittleEndianUInt() throws IOException {
    byte[] bytes = new byte[4];
    read(bytes, 0, 4);
    return StructConverter.bytesIntoUInt(bytes, 0);
  }
  
  public int readLittleEndianShort() throws IOException {
    byte[] bytes = new byte[2];
    read(bytes, 0, 2);
    return StructConverter.bytesIntoShort(bytes, 0);
  }

  void debug(byte[] bytes, int len) {
    for (int i = 0; i < len; i++) {
      System.out.print(Byte.toString(bytes[i]) + " ");
    }
    System.out.println();
  }

  public char readAsciiChar() throws IOException {
    byte b = readByte();
    return (char) b;
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
