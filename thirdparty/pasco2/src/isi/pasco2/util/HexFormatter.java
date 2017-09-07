//******************************************************************
// Released under the DevelopMentor OpenSource Software License.
// Please consult the LICENSE.jawin file in the project root directory,
// or at http://www.develop.com for details before using this
// software.
//******************************************************************

// From the JAWIN source distribution.
package isi.pasco2.util;

/**

 * Helper class for doing hex dumps of arrays to String.  Useful for 

 * debugging, esp. on IDEs that are not hex-friendly.

 */

public class HexFormatter {

	public static String convertBytesToString(byte[] bytes) {

		return convertBytesToString(bytes, 16, 0, true);

	}

	public static String convertBytesToString(
		byte[] bytes,
		int lineLength,
		int level,
		boolean showChars) {

		if (bytes == null) {

			return "(null array)";

		}

		return convertBytesToString(
			bytes,
			0,
			bytes.length,
			lineLength,
			level,
			showChars);

	}

	public static String convertBytesToString(
		byte[] bytes,
		int startOffset,
		int endOffset,
		int lineLength,
		int level,
		boolean showChars) {

		if (bytes == null) {

			return "(null array)";

		}

		int loop = 0;

		//each byte creates 3-4 chars in the  string, so the *5 makes a 

		//buffer that is large enough

		StringBuffer strb = new StringBuffer(bytes.length * 5);

		int boundary = (startOffset + lineLength - 1) % lineLength;

		int charLoop = 0;

		for (charLoop = loop = startOffset; loop < endOffset; loop++) {
			if ((loop % lineLength) == 0) {
				for (int j = 0; j < level + 1; j++) {
					strb.append("\t");
				}
			}

			strb.append(getHexString(bytes[loop]));

			if ((loop % lineLength) == boundary) {

				if (showChars) {

					strb.append(' ');

					while (charLoop <= loop) {

						strb.append(printChars[bytes[charLoop] & 0x00ff]);

						charLoop++;

					}

				}

				strb.append("\r\n");

			}

		}

		// Added this code to print chars of last line.

		if (showChars && (charLoop < loop)) {

			for (int gap = loop % lineLength; gap < lineLength; ++gap) {

				strb.append("   ");

			}

			strb.append(' ');

			while (charLoop < loop) {

				strb.append(printChars[bytes[charLoop] & 0x00ff]);

				charLoop++;

			}

		}

		return strb.toString();

	}

	public static String convertBytesToProxyTypeString(
		byte[] bytes,
		int startOffset,
		int endOffset,
		int lineLength,
		boolean showChars) {

		if (bytes == null) {
			return "(null array)";
		}

		int loop = 0;

		//each byte creates 3-4 chars in the  string, so the *5 makes a 

		//buffer that is large enough

		StringBuffer strb = new StringBuffer(bytes.length * 5);

		int boundary = (startOffset + lineLength - 1) % lineLength;

		int charLoop = 0;

		for (charLoop = loop = startOffset; loop < endOffset; loop++) {

			if (loop == 2) {
				strb.append("-");
			}
			strb.append(getHexProxyTypeString(bytes[loop]));

			/*if ((loop % lineLength) == boundary) 
			
			{
			
			if (showChars) 
			
			  {
			
			    strb.append(' ');
			
			    while (charLoop <= loop)    
			
				{
			
				  strb.append(printChars[bytes[charLoop] & 0x00ff]);
			
				  charLoop++;
			
				}
			
			  }
			
			strb.append("\r\n");
			
			}*/

		}

		// Added this code to print chars of last line.

		/*if ( showChars && ( charLoop < loop ) ) {
		
		  for ( int gap = loop%lineLength; gap < lineLength; ++gap ) {
		
		strb.append("   ");
		
		  }
		
		  strb.append(' ');
		
		  while ( charLoop < loop ) {
		
		strb.append( printChars[bytes[charLoop] & 0x00ff] );
		
		charLoop++;
		
		  }
		
		}*/

		return strb.toString();

	}

	public final static char[] hexchars = new char[16];

	public final static char[] printChars = new char[256];

	static {

		for (char n = 0; n < 10; n++)
			hexchars[n] = (char) ('0' + n);

		for (char n = 10; n < 16; n++)
			hexchars[n] = (char) ('A' - 10 + n);

		for (char n = 0; n < 32; n++)
			printChars[n] = '.';

		for (char n = 32; n < 256; n++)
			printChars[n] = (char) n;

		//for (char n=128; n<256; n++)

		//  printChars[n] = '.';

	}

	public static char[] getHexString(byte b) {

		char[] result = new char[3];

		result[0] = hexchars[(b & 0x00f0) >> 4];

		result[1] = hexchars[b & 0x000f];

		result[2] = ' ';

		return result;

	}

	public static char[] getHexProxyTypeString(byte b) {

		char[] result = new char[2];

		result[0] = hexchars[(b & 0x00f0) >> 4];

		result[1] = hexchars[b & 0x000f];

		//    result[2] = ' ';

		return result;

	}

}
