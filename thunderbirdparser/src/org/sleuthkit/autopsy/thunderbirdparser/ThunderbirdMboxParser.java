/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.thunderbirdparser;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author arivera
 */
public class ThunderbirdMboxParser  {

    /** Serial version UID */
    private static final long serialVersionUID = -1762689436731160661L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.application("mbox"));

    public static final String MBOX_MIME_TYPE = "application/mbox";
    public static final String MBOX_RECORD_DIVIDER = "From ";
    private static final Pattern EMAIL_HEADER_PATTERN = Pattern.compile("([^ ]+):[ \t]*(.*)");
    private static final Pattern EMAIL_ADDRESS_PATTERN = Pattern.compile("<(.*@.*)>");

    private static final String EMAIL_HEADER_METADATA_PREFIX = "MboxParser-";
    private static final String EMAIL_FROMLINE_METADATA = EMAIL_HEADER_METADATA_PREFIX + "from";
    private int numEmails = 0;
    
    private ThunderbirdXHTMLContentHandler xhtml =  null;
    private ArrayList<String> xhtmlDocs = new ArrayList<String>();
    
    private HashMap<String, Map<String,String>> emails = new HashMap<String, Map<String,String>>();

    private enum ParseStates {
        START, IN_HEADER, IN_CONTENT
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            ThunderbirdMetadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {

        InputStreamReader isr;
        try {
            // Headers are going to be 7-bit ascii
            isr = new InputStreamReader(stream, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new TikaException("US-ASCII is not supported!", e);
        }

        BufferedReader reader = new BufferedReader(isr);

        metadata.set(Metadata.CONTENT_TYPE, MBOX_MIME_TYPE);
        metadata.set(Metadata.CONTENT_ENCODING, "us-ascii");

        xhtml = new ThunderbirdXHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ThunderbirdMboxParser.ParseStates parseState = ThunderbirdMboxParser.ParseStates.START;
        String multiLine = null;
        boolean inQuote = false;
        

        // We're going to scan, line-by-line, for a line that starts with
        // "From "
        
        for (String curLine = reader.readLine(); curLine != null; curLine = reader.readLine())
        {
      
            boolean newMessage = curLine.startsWith(MBOX_RECORD_DIVIDER);
            if (newMessage) {
                numEmails += 1;
            }

            switch (parseState) {
            case START:
                if (newMessage) {
                    parseState = ThunderbirdMboxParser.ParseStates.IN_HEADER;
                    newMessage = false;
                    // Fall through to IN_HEADER
                } else {
                    break;
                }

            case IN_HEADER:
                if (newMessage) {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    //saveHeaderInMetadata(numEmails, metadata, curLine);
                    multiLine = curLine;
                }
                //I think this is never going to be true
                else if (curLine.length() == 0) 
                {
                    // Blank line is signal that we're transitioning to the content.
                 
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    parseState = ThunderbirdMboxParser.ParseStates.IN_CONTENT;

                    // Mimic what PackageParser does between entries.
                    xhtml.startElement("div", "class", "email-entry");
                    xhtml.startElement("p");
                    inQuote = false;
                }
                else if ((curLine.startsWith(" ") || curLine.startsWith("\t")) )
                {
                    multiLine += " " + curLine.trim();
                }
                else 
                {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    multiLine = curLine;
                }

                break;

                // TODO - use real email parsing support so we can correctly handle
                // things like multipart messages and quoted-printable encoding.
                // We'd also want this for charset handling, where content isn't 7-bit
                // ascii.
            case IN_CONTENT:
                if (newMessage) {
                    endMessage(inQuote);
                    parseState = ThunderbirdMboxParser.ParseStates.IN_HEADER;
                    multiLine = curLine;
                } else {
                    boolean quoted = curLine.startsWith(">");
                    if (inQuote) {
                        if (!quoted) {
                            xhtml.endElement("q");
                            inQuote = false;
                        }
                    } else if (quoted) {
                        xhtml.startElement("q");
                        inQuote = true;
                    }

                    xhtml.characters(curLine);

                    // For plain text email, each line is a real break position.
                    xhtml.element("br", "");
                }
            }
        }

        if (parseState == ThunderbirdMboxParser.ParseStates.IN_HEADER) {
            saveHeaderInMetadata(numEmails, metadata, multiLine);
        } else if (parseState == ThunderbirdMboxParser.ParseStates.IN_CONTENT) {
            endMessage(inQuote);
        }

        xhtml.endDocument();
        
        xhtmlDocs.add(xhtml.toString());
    }

    private void endMessage(boolean inQuote) throws SAXException {
        if (inQuote) {
            xhtml.endElement("q");
        }

        xhtml.endElement("p");
        xhtml.endElement("div");
    }

    private void saveHeaderInMetadata(int numEmails, ThunderbirdMetadata metadata, String curLine) 
    {
        
        //if ((curLine != null) && curLine.startsWith(MBOX_RECORD_DIVIDER) && (numEmails >= 1)) n
        //At this point, the current line we are feeding should never  be null!!!
        if ((curLine != null) && curLine.startsWith(MBOX_RECORD_DIVIDER)) 
        {
            metadata.add(EMAIL_FROMLINE_METADATA, curLine.substring(MBOX_RECORD_DIVIDER.length()));
            return;
        } 
        else if ((curLine == null)) {
            return;
        }

        Matcher headerMatcher = EMAIL_HEADER_PATTERN.matcher(curLine);
        if (!headerMatcher.matches()) {
            return; // ignore malformed header lines
        }

        String headerTag = headerMatcher.group(1).toLowerCase();
        String headerContent = headerMatcher.group(2);

        if (headerTag.equalsIgnoreCase("From")) {
            metadata.add(ThunderbirdMetadata.AUTHOR, headerContent);
             Matcher address = EMAIL_ADDRESS_PATTERN.matcher(headerContent);
            if(address.find()) {
        	metadata.add(ThunderbirdMetadata.CREATOR, address.group(1));
            } else if(headerContent.indexOf('@') > -1) {
        	metadata.add(ThunderbirdMetadata.CREATOR, headerContent);
            }
        } else if (headerTag.equalsIgnoreCase("To") ||
        	headerTag.equalsIgnoreCase("Cc") ||
        	headerTag.equalsIgnoreCase("Bcc")) {
            Matcher address = EMAIL_ADDRESS_PATTERN.matcher(headerContent);
            if(address.find()) {
        	metadata.add(ThunderbirdMetadata.MESSAGE_RECIPIENT_ADDRESS, address.group(1));
            } else if(headerContent.indexOf('@') > -1) {
        	metadata.add(ThunderbirdMetadata.MESSAGE_RECIPIENT_ADDRESS, headerContent);
            }
            
            String property = ThunderbirdMetadata.MESSAGE_TO;
            if (headerTag.equalsIgnoreCase("Cc")) {
        	property = ThunderbirdMetadata.MESSAGE_CC;
            } else if (headerTag.equalsIgnoreCase("Bcc")) {
        	property = ThunderbirdMetadata.MESSAGE_BCC;
            }
            metadata.add(property, headerContent);
        } else if (headerTag.equalsIgnoreCase("Subject")) {
            metadata.add(ThunderbirdMetadata.SUBJECT, headerContent);
            metadata.add(ThunderbirdMetadata.TITLE, headerContent);
        } else if (headerTag.equalsIgnoreCase("Date")) {
            try {
                Date date = parseDate(headerContent);
                metadata.set(ThunderbirdMetadata.DATE, date);
                metadata.set(ThunderbirdMetadata.CREATION_DATE, date);
            } catch (ParseException e) {
                // ignoring date because format was not understood
            }
        } else if (headerTag.equalsIgnoreCase("Message-Id")) {
            metadata.add(ThunderbirdMetadata.IDENTIFIER, headerContent);
        } else if (headerTag.equalsIgnoreCase("In-Reply-To")) {
            metadata.add(ThunderbirdMetadata.RELATION, headerContent);
        } else if (headerTag.equalsIgnoreCase("Content-Type")) {
            // TODO - key off content-type in headers to
            // set mapping to use for content and convert if necessary.

            metadata.add(ThunderbirdMetadata.CONTENT_TYPE, headerContent);
            metadata.add(ThunderbirdMetadata.FORMAT, headerContent);
        } else {
            metadata.add(EMAIL_HEADER_METADATA_PREFIX + headerTag, headerContent);
        }
    }
    
    public static Date parseDate(String headerContent) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        return dateFormat.parse(headerContent);
    }
    
    public int getNumberOfEmails()
    {
        return this.numEmails;
    }
    
    public ArrayList<String> getXHTMLDocs()
    {
        return this.xhtmlDocs;
    }
    
    

            
    public void parseMbox(
            InputStream stream, ContentHandler handler,
            ThunderbirdMetadata metadata, ParseContext context)
            throws IOException, TikaException, SAXException {

        InputStreamReader isr;
        try {
            // Headers are going to be 7-bit ascii
            isr = new InputStreamReader(stream, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new TikaException("US-ASCII is not supported!", e);
        }

        BufferedReader reader = new BufferedReader(isr);

        metadata.set(Metadata.CONTENT_TYPE, MBOX_MIME_TYPE);
        metadata.set(Metadata.CONTENT_ENCODING, "us-ascii");

        xhtml = new ThunderbirdXHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        ThunderbirdMboxParser.ParseStates parseState = ThunderbirdMboxParser.ParseStates.START;
        String multiLine = null;
        String curLine = null;
        boolean inQuote = false;
        int tempEmailsCount = 0;
        

        // We're going to scan, line-by-line, for a line that starts with
        // "From "
        
        while((curLine = reader.readLine()) != null)
        {
      
            boolean newMessage = curLine.startsWith(MBOX_RECORD_DIVIDER);
            if (newMessage) 
            {
                //At this point we should have the first email metadata
                tempEmailsCount = numEmails;
                if(numEmails > 0)
                {
                    xhtml.endDocument();
                    formatEmailList(metadata,xhtml.toString());
                    //let's clear the metatada and content
                    metadata  = new ThunderbirdMetadata();
                    handler = new BodyContentHandler(10*1024*1024); 
                    xhtml = new ThunderbirdXHTMLContentHandler(handler, metadata);  
                    xhtml.startDocument();
                }
                    
                numEmails += 1;
                parseState = ThunderbirdMboxParser.ParseStates.IN_HEADER;
                newMessage = false;   
            }
            //At this point we reached the last email
//            else if ((this.emails != null && this.emails.size() < numEmails) && (tempEmailsCount > 0 && tempEmailsCount < numEmails))
//            {
//                xhtml.endDocument();
//                formatEmailList(metadata,xhtml.toString());
//                //let's clear the metatada and content
//                metadata  = new ThunderbirdMetadata();
//                handler = new BodyContentHandler(); 
//                xhtml = new ThunderbirdXHTMLContentHandler(handler, metadata);  
//                xhtml.startDocument();                
//            }

            if(parseState == ParseStates.START)
            {
                parseState = ThunderbirdMboxParser.ParseStates.IN_HEADER;
                newMessage = false;                
            }
            else if (parseState == ParseStates.IN_HEADER)
            {
                //Start extracting metadata
                if (newMessage) {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    //saveHeaderInMetadata(numEmails, metadata, curLine);
                    multiLine = curLine;
                }
                //I think this is never going to be true
                else if (curLine.length() == 0) 
                {
                    // Blank line is signal that we're transitioning to the content.
                 
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    parseState = ThunderbirdMboxParser.ParseStates.IN_CONTENT;

                    // Mimic what PackageParser does between entries.
                    xhtml.startElement("div", "class", "email-entry");
                    xhtml.startElement("p");
                    inQuote = false;
                }
                else if ((curLine.startsWith(" ") || curLine.startsWith("\t")) )
                {
                    multiLine += " " + curLine.trim();
                }
                else 
                {
                    saveHeaderInMetadata(numEmails, metadata, multiLine);
                    multiLine = curLine;
                }
                
            }
            else if (parseState == ParseStates.IN_CONTENT)
            {
                if (newMessage) {
                    endMessage(inQuote);
                    parseState = ThunderbirdMboxParser.ParseStates.IN_HEADER;
                    multiLine = curLine;
                } else {
                    boolean quoted = curLine.startsWith(">");
                    if (inQuote) {
                        if (!quoted) {
                            xhtml.endElement("q");
                            inQuote = false;
                        }
                    } else if (quoted) {
                        xhtml.startElement("q");
                        inQuote = true;
                    }

                    xhtml.characters(curLine);

                    // For plain text email, each line is a real break position.
                    xhtml.element("br", "");
                }
            }
        }

        if (parseState == ThunderbirdMboxParser.ParseStates.IN_HEADER) {
            saveHeaderInMetadata(numEmails, metadata, multiLine);
        } else if (parseState == ThunderbirdMboxParser.ParseStates.IN_CONTENT) {
            endMessage(inQuote);
        }
        if (numEmails > this.emails.size())
        {
            //Grab the last email file metadata and content
            xhtml.endDocument();
            formatEmailList(metadata,xhtml.toString());
        }

        xhtml.endDocument();
    }
    
    private void formatEmailList(ThunderbirdMetadata metadata, String emailContent)
    {
        Map<String,String> emailMetaContent = new HashMap<String,String>();
        //HashMap<String,Map<String,String>> email = new HashMap<String,Map<String,String>>();
        
        //Fill the email metadata and content(message)
        emailMetaContent.put(Metadata.MESSAGE_TO, metadata.get(Metadata.MESSAGE_TO));
        emailMetaContent.put(Metadata.MESSAGE_CC, metadata.get(Metadata.MESSAGE_CC));
        emailMetaContent.put(Metadata.MESSAGE_BCC, metadata.get(Metadata.MESSAGE_BCC));
        emailMetaContent.put(Metadata.AUTHOR, metadata.get(Metadata.AUTHOR));
        emailMetaContent.put("content", emailContent);
        emailMetaContent.put("date", metadata.get("date"));
        emailMetaContent.put(Metadata.SUBJECT, metadata.get(Metadata.SUBJECT));
        
        this.emails.put(metadata.get(ThunderbirdMetadata.IDENTIFIER), emailMetaContent);
    }
    
    public HashMap<String,Map<String,String>> getAllEmails()
    {
        return this.emails;
    }
}
