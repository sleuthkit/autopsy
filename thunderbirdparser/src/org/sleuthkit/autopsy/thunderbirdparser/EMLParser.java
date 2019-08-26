/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.thunderbirdparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.BinaryBody;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author kelly
 */
public class EMLParser extends MimeJ4MessageParser{
    
    private static final Logger logger = Logger.getLogger(EMLParser.class.getName());
    

    static boolean isEMLFile(AbstractFile abFile, byte[] buffer) {
        String ext = abFile.getNameExtension();
        boolean isEMLFile = ext != null ? ext.equals("eml") : false;
        if(isEMLFile) {
             isEMLFile =  (new String(buffer)).contains("To:"); //NON-NLS
        }
        
        return isEMLFile;
    }
    
    static EmailMessage parse(AbstractFile sourceFile, String localPath) throws FileNotFoundException, IOException, MimeException {
        try (FileInputStream fis = new FileInputStream(localPath)){  
            //Create message with stream from file  
            //If you want to parse String, you can use:  
            //Message mimeMsg = new Message(new ByteArrayInputStream(mimeSource.getBytes()));  
            
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).setMaxHeaderCount(-1).build();
            // disable line length checks.
            messageBuilder.setMimeEntityConfig(config);
            Message mimeMsg = messageBuilder.parseMessage(fis);


            return (new EMLParser()).extractEmail(mimeMsg, localPath, sourceFile.getId());
        }
    }

}
