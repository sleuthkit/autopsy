/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.keywordsearch.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.Decryptor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.keywordsearch.SolrSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An action that will allow the user to enter a password for document file and
 * read its contents.
 *
 */
public class ExtractDocumentWithPasswordAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ExtractDocumentWithPasswordAction.class.getName());
    private final AbstractFile documentFile;
    private final FileManager fileManager;
    private final Path decryptedFilePathAbsolute;
    private final String decryptedFilePathRelative;
    private final Case currentCase;

    /**
     * Create an action that will allow the user to enter an password and then
     * use the entered password to read the contents of a password protected
     * document.
     *
     * @param file the password protected document to read
     */
    @Messages({"ExtractDocumentWithPasswordAction.name.text=Extract Content with Password",
        "ExtractDocumentWithPasswordAction.prompt.text=Enter Password",
        "ExtractDocumentWithPasswordAction.prompt.title=Enter Password",
        "ExtractDocumentWithPasswordAction.extractFailed.title=Failed to Open File with Password",
        "# {0} - document",
        "ExtractDocumentWithPasswordAction.progress.text=Extracting contents of document: {0}"})
    public ExtractDocumentWithPasswordAction(AbstractFile file) throws NoCurrentCaseException {
        super(Bundle.ExtractDocumentWithPasswordAction_name_text());
        documentFile = file;
        currentCase = Case.getCurrentCaseThrows();
        decryptedFilePathAbsolute = Paths.get(currentCase.getModuleDirectory(), "keywordsearch", "extracted");
        decryptedFilePathRelative = Paths.get(currentCase.getModuleOutputDirectoryRelativePath(), "keywordsearch", "extracted").toString();
        fileManager = currentCase.getServices().getFileManager();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            String password = getPassword(Bundle.ExtractDocumentWithPasswordAction_prompt_title(), "");
            if (password != null) {
                ReadContentInputStream stream = new ReadContentInputStream(documentFile);
                switch (documentFile.getNameExtension().toLowerCase()) {
                    case ("doc"):
                        decryptDoc(password, stream);
                        break;
                    case ("xls"):
                        decryptXls(password, stream);
                        break;
                    case ("ppt"):
                        decryptPpt(password, stream);
                        break;
                    case ("docx"):
                        decryptDocx(password, stream);
                        break;
                    case ("xlsx"):
                        decryptXlsx(password, stream);
                        break;
                    case ("pptx"):
                        decryptPptx(password, stream);
                        break;
                    case ("pdf"):
                        decryptPdf(password, stream);
                        break;
                    default:
                        throw new CaseActionException(documentFile.getNameExtension() + " NOT SUPPORTED");
                }
                DerivedFile newFile = fileManager.addDerivedFile(documentFile.getName(), decryptedFilePathRelative + File.separator + documentFile.getName(), documentFile.getSize(),
                        documentFile.getCtime(), documentFile.getCrtime(), documentFile.getAtime(), documentFile.getAtime(),
                        true, documentFile, null, "Embedded File Extractor", null, null, TskData.EncodingType.NONE);
                KeywordSearchService kwsService = new SolrSearchService();
                kwsService.index(newFile);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE,"IO EXCEPTION TRYING TO DECRYPT", ex);
        } catch (CaseActionException ex) {
            logger.log(Level.INFO,"invalid extension", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE,"TskCoreException adding derived file", ex);
        } catch (GeneralSecurityException ex) {
            logger.log(Level.WARNING,"GeneralSecurityException parsing document",ex);
        }
    }

    private void decryptDoc(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HWPFDocument doc = new HWPFDocument(filesystem.getRoot());
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptXls(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HSSFWorkbook doc = new HSSFWorkbook(filesystem.getRoot(), true);
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptPpt(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HSLFSlideShow doc = new HSLFSlideShow(filesystem.getRoot());
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptDocx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XWPFDocument doc = new XWPFDocument(unpasswordedStream);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptXlsx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XSSFWorkbook doc = new XSSFWorkbook(unpasswordedStream);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptPptx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XMLSlideShow doc = new XMLSlideShow(unpasswordedStream);
        try (OutputStream os = getOutputStream()) {
            doc.write(os);
        }
    }

    private void decryptPdf(String password, ReadContentInputStream stream) throws IOException {
        PDDocument doc = PDDocument.load(stream, password);
        doc.setAllSecurityToBeRemoved(true);
        try (OutputStream os = getOutputStream()) {
            doc.save(os);
        }
    }

    private InputStream getOoxmlInputStream(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException {
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        EncryptionInfo info = new EncryptionInfo(filesystem);
        Decryptor d = Decryptor.getInstance(info);
        if (!d.verifyPassword(password)) {
            throw new RuntimeException("Unable to process: document is encrypted");
        }
        return d.getDataStream(filesystem);
    }

    private OutputStream getOutputStream() throws IOException {
        if (!decryptedFilePathAbsolute.toFile().exists()) {
            Files.createDirectories(decryptedFilePathAbsolute);
        }
        return new FileOutputStream(decryptedFilePathAbsolute.toString() + File.separator + documentFile.getName());
    }

    private String getPassword(String title, String oldPassword) {
        String password = null;
        Object inputValue = JOptionPane.showInputDialog(WindowManager.getDefault().getMainWindow(), Bundle.ExtractDocumentWithPasswordAction_prompt_text(),
                title, JOptionPane.PLAIN_MESSAGE, null, null, oldPassword);
        if (inputValue != null) {
            password = (String) inputValue;
        }
        return password;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }

}
