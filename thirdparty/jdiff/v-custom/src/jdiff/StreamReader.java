package jdiff;

import java.util.*;
import java.io.*;

/**
 * Reads in lines from an input stream and displays them.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com.
 */
class StreamReader extends Thread {
    /** The input stream. */
    InputStream is_;
        
    /** Constructor which takes an InputStream. */
    StreamReader(InputStream is) {
        is_ = is;
    }
        
    /** Method which is called when this thread is started. */
    public void run() {
        try {
            InputStreamReader isr = new InputStreamReader(is_);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while((line = br.readLine()) != null)
                System.out.println(line);    
        } catch (IOException ioe) {
            System.out.println("IO Error invoking Javadoc");
            ioe.printStackTrace();  
        } catch (Exception e) {
            // Ignore read errors which indicate that the process is complete
        }
    }
}
