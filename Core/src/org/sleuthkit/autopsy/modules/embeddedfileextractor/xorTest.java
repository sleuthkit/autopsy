/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.IOException;
/**
 *
 */
public class xorTest extends BufferedOutputStream{
    final private String HEADER = "XOR_AUTOPSY_HEADER_xxxxxxxxxxxxx";
    final private int HEADER_LENGTH = HEADER.length();
    
    public xorTest(OutputStream out) throws IOException{
        super(out);
        writeHeader();
    }
    
    public xorTest(OutputStream out, int size) throws IOException{
        super(out, size);
        writeHeader();
    }
    
    private void writeHeader() throws IOException{
        write(HEADER.getBytes(), 0, HEADER_LENGTH);
    }
    
    private byte encode(byte b){
        return ((byte)(b ^ 0xa5)); 
    }
    
    @Override
    public void write(int b) throws IOException{
        super.write((int)encode((byte)b));
    }
    
    @Override
    public void write(byte[] b,
                  int off,
                  int len)
           throws IOException{
        byte[] encodedData = b.clone(); // Could be more efficient
        for(int i = 0;i < b.length;i++){
            encodedData[i] = encode(b[i]);
        }
        
        super.write(encodedData, off, len);
    }
}
