package org.sleuthkit.autopsy.mboxparser;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mbox.MboxParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class MboxEmailParser {
    
    
    private InputStream stream;
    //Tika object
    private Tika tika;
    private Metadata metadata;
    private ContentHandler contentHandler;
    private String mimeType;   
    private Parser parser;
    private ParseContext context;
    
    private static ArrayList<String> tikaMimeTypes;
    
    static
    {
        tikaMimeTypes = new ArrayList<String>();       
        tikaMimeTypes.add(MimeTypes.OCTET_STREAM);
        tikaMimeTypes.add(MimeTypes.PLAIN_TEXT);
        tikaMimeTypes.add(MimeTypes.XML);
    }
    
    public MboxEmailParser() 
    {
        this.tika = new Tika();
    }
    
    public MboxEmailParser(InputStream inStream) 
    {
        this.tika = new Tika();
        this.stream = inStream;
    }
    
    public MboxEmailParser(String filepath) 
    {
        this.tika = new Tika();
        this.stream = this.getClass().getResourceAsStream(filepath);
    }
    
    private void init() throws IOException
    {        
        this.metadata = new Metadata();           
        //Set MIME Type    
        this.mimeType = tika.detect(this.stream);  
        this.parser   = new MboxParser();   
        this.context  = new ParseContext();
        this.contentHandler = new BodyContentHandler();
        //Seems like setting this causes the metadata not to output all of it.
        this.metadata.set(Metadata.CONTENT_TYPE, this.mimeType);
    }
    
    public void parse() throws FileNotFoundException, IOException, SAXException, TikaException
    {   
        init();
        // this.metadata = new Metadata();        
        //String mimeType = tika.detect(this.stream);        
        parser.parse(this.stream,this.contentHandler, this.metadata, context);
    }
    
    public void parse(InputStream inStream) throws FileNotFoundException, IOException, SAXException, TikaException
    {   
        init();        
        parser.parse(inStream,this.contentHandler, this.metadata, context);
    }
    
    public Metadata getMetadata()
    {
        return this.metadata;
    }
    
    //Returns message content, i.e. plain text or html
    public String getContent()
    {
        return this.contentHandler.toString();
    }
    
    public String detectEmailFileFormat(String filepath) throws IOException
    {
        return this.tika.detect(filepath);
    }
    
    //Detects the mime type from the first few bytes of the document
    public String detectMediaTypeFromBytes(byte[] firstFewBytes, String inDocName)
    {
        return this.tika.detect(firstFewBytes, inDocName);
    }
    
    
    public boolean isValidMbox(byte[] buffer)
    {
        return (new String(buffer)).startsWith("From ");
    }
    
    //This assumes the file/stream was parsed since we are looking at the metadata
    public boolean isValidMboxType()
    {
        return this.metadata.get(Metadata.CONTENT_TYPE).equals("application/mbox");
    }
    
    //Get email subject
    public String getSubject()
    {
        return this.metadata.get(Metadata.SUBJECT);
    }
    
    public String getTitle()
    {
        return this.metadata.get(Metadata.TITLE);
    }
    
    public Long getDateCreated() 
    {
        Long epochtime;
        Long ftime = (long) 0;
        try {
            epochtime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse(this.metadata.get(Metadata.DATE_CREATED)).getTime();
            ftime = epochtime.longValue();
            ftime = ftime / 1000;
        } catch (ParseException ex) {
            Logger.getLogger(MboxFileIngestService.class.getName()).log(Level.SEVERE, null, ex);
        }

        return ftime;
    }
    
    public String getContenType()
    {
        return this.metadata.get(Metadata.CONTENT_TYPE);
    }
    
    public String getContenEncoding()
    {
        return this.metadata.get(Metadata.CONTENT_ENCODING);
    }
    
    public String getFrom()
    {
        return this.metadata.get(Metadata.MESSAGE_FROM);
    }
    
    public String getTo()
    {
        return this.metadata.get(Metadata.MESSAGE_TO);
    }
    
    public String getCC()
    {
        return this.metadata.get(Metadata.MESSAGE_CC);
    }
    
    public String getBCC()
    {
        return this.metadata.get(Metadata.MESSAGE_BCC);
    }
    
    public String getRecipientAddress()
    {
        return this.metadata.get(Metadata.MESSAGE_RECIPIENT_ADDRESS);
    }
    
    public String getMboxSupportedMediaType()
    {
        return MediaType.application("mbox").getType();
    }
}
