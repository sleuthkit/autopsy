package org.lobobrowser.util.io;

import java.io.IOException;
import java.io.Reader;

public class EmptyReader extends Reader {
	public void close() throws IOException {
	}

	public int read(char[] cbuf, int off, int len) throws IOException {
		return 0;
	}
}
