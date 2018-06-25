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
import org.apache.poi.EncryptedDocumentException;
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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchModuleException;
import org.sleuthkit.autopsy.keywordsearch.SolrSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.EncodedFileOutputStream;

/**
 * An action that will allow the user to enter a password for document file and
 * read its contents.
 */
final class ExtractDocumentWithPasswordAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ExtractDocumentWithPasswordAction.class.getName());
    private static final String PASSWORD_REMOVED_STRING = "_password_removed";
    private final AbstractFile abstractFile;
    private final FileManager fileManager;
    private final Path decryptedFilePathAbsolute;
    private final String decryptedFilePathRelative;

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
        "ExtractDocumentWithPasswordAction.prompt.retryTitle=Incorrect Password",
        "ExtractDocumentWithPasswordAction.extractFailed.title=Failed to Open File with Password",
        "# {0} - document",
        "ExtractDocumentWithPasswordAction.progress.text=Extracting contents of document: {0}"})
    ExtractDocumentWithPasswordAction(AbstractFile file) throws NoCurrentCaseException {
        super(Bundle.ExtractDocumentWithPasswordAction_name_text());
        abstractFile = file;
        Case currentCase = Case.getCurrentCaseThrows();
        decryptedFilePathAbsolute = Paths.get(currentCase.getModuleDirectory(), "keywordsearch", "extracted");
        decryptedFilePathRelative = Paths.get(currentCase.getModuleOutputDirectoryRelativePath(), "keywordsearch", "extracted").toString();
        fileManager = currentCase.getServices().getFileManager();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String password = "";
        boolean incorrectPassword = true;
        while (incorrectPassword) {
            //loop until they enter a correct password, no password, or there is a non-password related error
            try {
                String title;
                if (password != null && password.isEmpty()) {
                    title = Bundle.ExtractDocumentWithPasswordAction_prompt_title();
                } else {
                    title = Bundle.ExtractDocumentWithPasswordAction_prompt_retryTitle();
                }
                password = getPassword(title, password);
                if (password != null && !password.isEmpty()) {
                    ReadContentInputStream stream = new ReadContentInputStream(abstractFile);
                    switch (abstractFile.getNameExtension().toLowerCase()) {
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
                            throw new KeywordSearchModuleException("File extension of " + abstractFile.getNameExtension()
                                    + "is not supported by the extract document with password action");

                    }
                    incorrectPassword = false;
                    DerivedFile newFile = fileManager.addDerivedFile(abstractFile.getName() + PASSWORD_REMOVED_STRING + abstractFile.getNameExtension(), decryptedFilePathRelative + File.separator + abstractFile.getName() + PASSWORD_REMOVED_STRING + abstractFile.getNameExtension(), abstractFile.getSize(),
                            abstractFile.getCtime(), abstractFile.getCrtime(), abstractFile.getAtime(), abstractFile.getAtime(),
                            true, abstractFile, null, "Embedded File Extractor", null, null, TskData.EncodingType.XOR1);
                    KeywordSearchService kwsService = new SolrSearchService();
                    kwsService.index(newFile);
                } else {
                    incorrectPassword = false;
                }
            } catch (EncryptedDocumentException ex) {
                logger.log(Level.WARNING, "Encyption security certificates not found unable to use password", ex);
            } catch (BadPasswordException ex) {
                logger.log(Level.INFO, "Incorrect password of " + password + " entered for " + abstractFile.getName(), ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error encountered while trying to decrypt " + abstractFile.getName() + "with password " + password, ex);
            } catch (KeywordSearchModuleException ex) {
                logger.log(Level.INFO, "Extract document with password action not supported for " + abstractFile.getName(), ex);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error encountered adding derived file with password removed to case ", ex);
            } catch (GeneralSecurityException ex) {
                logger.log(Level.WARNING, "Error parsing file " + abstractFile.getName() + "with password " + password, ex);
            }
        }
    }

    /**
     * Read a Ppt with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Ppt
     * @param stream   - the stream containing the Ppt to read
     *
     * @throws IOException
     */
    private void decryptDoc(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HWPFDocument doc = new HWPFDocument(filesystem.getRoot());
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a Xls with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Xls
     * @param stream   - the stream containing the Xls to read
     *
     * @throws IOException
     */
    private void decryptXls(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HSSFWorkbook doc = new HSSFWorkbook(filesystem.getRoot(), true);
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a Doc with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Doc
     * @param stream   - the stream containing the Doc to read
     *
     * @throws IOException
     */
    private void decryptPpt(String password, ReadContentInputStream stream) throws IOException {
        Biff8EncryptionKey.setCurrentUserPassword(password);
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        HSLFSlideShow doc = new HSLFSlideShow(filesystem.getRoot());
        Biff8EncryptionKey.setCurrentUserPassword(null);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a Docx with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Docx
     * @param stream   - the stream containing the Docx to read
     *
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private void decryptDocx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException, BadPasswordException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XWPFDocument doc = new XWPFDocument(unpasswordedStream);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a Xlsx with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Xlsx
     * @param stream   - the stream containing the Xlsx to read
     *
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private void decryptXlsx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException, BadPasswordException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XSSFWorkbook doc = new XSSFWorkbook(unpasswordedStream);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a Pptx with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the Pptx
     * @param stream   - the stream containing the Pptx to read
     *
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private void decryptPptx(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException, BadPasswordException {
        InputStream unpasswordedStream = getOoxmlInputStream(password, stream);
        XMLSlideShow doc = new XMLSlideShow(unpasswordedStream);
        try (OutputStream outStream = getOutputStream()) {
            doc.write(outStream);
        }
    }

    /**
     * Read a PDF with the provided password and make a copy of it without its
     * password.
     *
     * @param password - the password to try when reading the PDF
     * @param stream   - the stream containing the PDF to read
     *
     * @throws IOException
     * @throws
     * org.sleuthkit.autopsy.keywordsearch.actions.ExtractDocumentWithPasswordAction.BadPasswordException
     */
    private void decryptPdf(String password, ReadContentInputStream stream) throws IOException, BadPasswordException {
        PDDocument doc;
        try {
            doc = PDDocument.load(stream, password);
        } catch (IOException ex) {
            throw new BadPasswordException("Unable to load stream stream for pdf with password", ex);
        }
        doc.setAllSecurityToBeRemoved(true);
        try (OutputStream outStream = getOutputStream()) {
            doc.save(outStream);
        }
        if (doc.isEncrypted()) {
            throw new BadPasswordException("Unable to process: password for encrypted document is not correct");
        }
    }

    /**
     * Get an input stream for the password protected xml based documents such
     * as xlsx, docx, and pptx.
     *
     * @param password - the password to use for reading the file
     * @param stream   - the input stream containing the file
     *
     * @return stream - the inputStream to read the file from
     *
     * @throws IOException
     * @throws GeneralSecurityException
     * @throws
     * org.sleuthkit.autopsy.keywordsearch.actions.ExtractDocumentWithPasswordAction.BadPasswordException
     */
    private InputStream getOoxmlInputStream(String password, ReadContentInputStream stream) throws IOException, GeneralSecurityException, BadPasswordException {
        NPOIFSFileSystem filesystem = new NPOIFSFileSystem(stream);
        EncryptionInfo info = new EncryptionInfo(filesystem);
        Decryptor decryptor = Decryptor.getInstance(info);
        if (!decryptor.verifyPassword(password)) {
            throw new BadPasswordException("Unable to process: password for encrypted document is not correct");
        }
        return decryptor.getDataStream(filesystem);
    }

    /**
     * Get an output stream for writing of a copy of the file without password
     * protection.
     *
     * @return the EncodedFileOutputStream which the file will be written to.
     *
     * @throws IOException
     */
    private EncodedFileOutputStream getOutputStream() throws IOException {
        if (!decryptedFilePathAbsolute.toFile().exists()) {
            Files.createDirectories(decryptedFilePathAbsolute);
        }
        return new EncodedFileOutputStream(new FileOutputStream(
                decryptedFilePathAbsolute.toString() + File.separator + abstractFile.getName() + PASSWORD_REMOVED_STRING + abstractFile.getNameExtension()),
                TskData.EncodingType.XOR1);
    }

    /**
     * Get a password from the user.
     *
     * @param title       the title of dialog to prompt for a password
     * @param oldPassword the password which was entered previously
     *
     * @return the password which was entered
     */
    private String getPassword(String title, String oldPassword) {
        String password = null;
        Object inputValue = JOptionPane.showInputDialog(WindowManager.getDefault().getMainWindow(), Bundle.ExtractDocumentWithPasswordAction_prompt_text(),
                title, JOptionPane.PLAIN_MESSAGE, null, null, oldPassword);
        if (inputValue != null) {
            password = (String) inputValue;
        }
        return password;
    }

    /**
     * Exception for when an incorrect password is entered.
     */
    private final class BadPasswordException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Create a BadPasswordException with the specified message.
         *
         * @param message - the message to be associated with the exception.
         */
        BadPasswordException(String message) {
            super(message);
        }

        /**
         * Create a BadPasswordException with the specified message.
         *
         * @param message - the message to be associated with the exception.
         * @param cause   - the exception which caused this exception to be
         *                created
         */
        BadPasswordException(String message, Throwable cause) {
            super(message, cause);
        }

    }

}
