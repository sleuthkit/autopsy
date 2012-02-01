package isi.pasco2.util;

//******************************************************************
//Released under the DevelopMentor OpenSource Software License.
//Please consult the LICENSE.jawin file in the project root directory,
//or at http://www.develop.com for details before using this
//software.
//******************************************************************
//From the JAWIN source distribution.

public class StructConverter {
    /*
    
      public static INative structToMem(IStruct s)
    
      throws COMException
    
      {
    
      byte[] bytes = s.instToBytes();
    
      System.out.println(HexFormatter.convertBytesToString(bytes, 16, true));
    
      if (bytes == null)  {
    
      throw new Error("reflective marshalling not implemented yet");
    
      }
    
      MemPtr mp = new MemPtr(bytes);
    
      return mp;
    
      }
    
    */
    static public long parseLong(String val) {
        int length = val.length();
        if (length > 16) {
            throw new NumberFormatException("too many digits");
        }
        int shift = 0;
        long res = 0;
        for (int i = length - 1; i >= 0; i--) {
            res = res + ((long) Character.digit(val.charAt(i), 16) << shift);
            shift += 4;
        }
        return res;
    }
    static public int parseInt(String val) {
        int length = val.length();
        if (length > 8) {
            throw new NumberFormatException("too many digits");
        }
        int shift = 0;
        int res = 0;
        for (int i = length - 1; i >= 0; i--) {
            res = res + (Character.digit(val.charAt(i), 16) << shift);
            shift += 4;
        }
        return res;
    }
    static public short parseShort(String val) {
        int length = val.length();
        if (length > 4) {
            throw new NumberFormatException("too many digits");
        }
        int shift = 0;
        int res = 0;
        for (int i = length - 1; i >= 0; i--) {
            res = res + (Character.digit(val.charAt(i), 16) << shift);
            shift += 4;
        }
        return (short) res;
    }
    static public int longIntoBEBytes(long data, byte[] bytes, int start) {
        bytes[start++] = (byte) (data >>> 56);
        bytes[start++] = (byte) (data >>> 48);
        bytes[start++] = (byte) (data >>> 40);
        bytes[start++] = (byte) (data >>> 32);
        bytes[start++] = (byte) (data >>> 24);
        bytes[start++] = (byte) (data >>> 16);
        bytes[start++] = (byte) (data >>> 8);
        bytes[start++] = (byte) (data);
        return start;
    }
    static public long bytesIntoLong(byte[] bytes, int offset) {
        int nLo = bytesIntoInt(bytes, offset);
        int nHi = bytesIntoInt(bytes, offset + 4);
        return ((long) (nHi) << 32) + (nLo & 0xFFFFFFFFL);
    }
    static public double bytesIntoDouble(byte[] bytes, int offset) {
        double d = Double.longBitsToDouble(bytesIntoLong(bytes, offset));
        return d;
    }
    static public boolean bytesIntoBoolean(byte[] bytes, int offset) {
        return bytes[offset] != 0;
    }
    static public int bytesIntoInt(byte[] bytes, int offset) {
        int l =
            (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
        return l;
    }
    
    static public long bytesIntoUInt(byte[] bytes, int offset) {
      long l = ( ((long)bytes[offset] & 0xff)
              | (((long)bytes[offset + 1] & 0xff) << 8)
              | (((long)bytes[offset + 2] & 0xff) << 16)
              | (((long)bytes[offset + 3] & 0xff) << 24) ) ;
      return l;
  }
    static public int BEBytesIntoInt(byte[] bytes, int offset) {
        int l =
            (bytes[offset + 3] & 0xff)
                | ((bytes[offset + 2] & 0xff) << 8)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 0] & 0xff) << 24);
        return l;
    }
    static public short bytesIntoShort(byte[] bytes, int offset) {
        int l = (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        return (short) l;
    }
    static public int longIntoBytes(long data, byte[] bytes, int start) {
        bytes[start++] = (byte) (data);
        bytes[start++] = (byte) (data >>> 8);
        bytes[start++] = (byte) (data >>> 16);
        bytes[start++] = (byte) (data >>> 24);
        bytes[start++] = (byte) (data >>> 32);
        bytes[start++] = (byte) (data >>> 40);
        bytes[start++] = (byte) (data >>> 48);
        bytes[start++] = (byte) (data >>> 56);
        return start;
    }
    static public int intIntoBytes(int data, byte[] bytes, int start) {
        bytes[start++] = (byte) (data);
        bytes[start++] = (byte) (data >>> 8);
        bytes[start++] = (byte) (data >>> 16);
        bytes[start++] = (byte) (data >>> 24);
        return start;
    }
    static public int shortIntoBytes(short data, byte[] bytes, int start) {
        bytes[start++] = (byte) (data);
        bytes[start++] = (byte) (data >>> 8);
        return start;
    }
    static public int byteArrayIntoBytes(byte[] src, byte[] dest, int start) {
        System.arraycopy(src, 0, dest, start, src.length);
        return start + src.length;
    }


    static public int stringIntoNullTerminatedASCIIBytes(
        String str,
        byte[] bytes,
        int start) {
        int index = stringIntoASCIIBytes(str, bytes, start);
        bytes[index++] = 0;
        return index;
    }

    static public int stringIntoASCIIBytes(
        String str,
        byte[] bytes,
        int start) {
        if (str == null) {
            return -1;
        }
        for (int n = 0; n < str.length(); n++) {
            bytes[start++] = (byte) str.charAt(n);
        }
        return start;
    }

    /*
     * Does not null terminate the bytes
     */
    static public int stringIntoUnicodeBytes(
        String data,
        byte[] bytes,
        int start) {
        for (int i = 0; i < data.length(); i++) {
            int v = data.charAt(i);
            bytes[start++] = (byte) ((v >>> 0) & 0xFF);
            bytes[start++] = (byte) ((v >>> 8) & 0xFF);
        }
        return start;
    }
    static public int stringIntoNullTermintedUnicodeBytes(
        String data,
        byte[] bytes,
        int start) {
        start = stringIntoUnicodeBytes(data, bytes, start);
        bytes[start++] = (byte) 0;
        bytes[start++] = (byte) 0;
        return start;
    }
    /*
    * Does not null terminate the bytes
    */
    static public String unicodeBytesIntoString(
        byte[] bytes,
        int offset,
        int length) {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int v =
                ((bytes[offset + i * 2]) | (bytes[offset + i * 2 + 1] << 8));
            char c = (char) v;
            res.append(c);
        }
        return res.toString();
    }
    /*
    * Does deal with null terminated bytes
    */
    static public String nullTerminatedUnicodeBytesIntoString(
        byte[] bytes,
        int offset,
        int length) {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int v =
                ((bytes[offset + i * 2]) | (bytes[offset + i * 2 + 1] << 8));
            if (v == 0) {
                break;
            } else {
                char c = (char) v;
                res.append(c);
            }
        }
        return res.toString();
    }
    static public String nullTerminatedAsciiBytesIntoString(
        byte[] bytes,
        int offset,
        int length) {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int v = (bytes[offset + i]);
            if (v == 0)
                break;
            char c = (char) v;
            res.append(c);
        }
        return res.toString();
    }
    static public String asciiBytesIntoString(
        byte[] bytes,
        int offset,
        int length) {
        StringBuffer res = new StringBuffer();
        for (int i = 0; i < length; i++) {
            int v = (bytes[offset + i]);
            char c = (char) v;
            res.append(c);
        }
        return res.toString();
    }
    
    public static short lowword(int word) {
        return (short) (word & 0x0000FFFF);
    }
    
    public static short highword(int word) {
        return (short) ((word & 0xFFFF0000) >> 16);
    }
    
    public static byte lowbyte(short word) {
        return (byte) (word & 0x00FF);
    }
    
    public static byte highbyte(short word) {
        return (byte) ((word & 0xFF00) >> 8);
    }
}

