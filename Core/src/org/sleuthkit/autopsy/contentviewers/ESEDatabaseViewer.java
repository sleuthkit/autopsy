/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.contentviewers.EdbExplorerTool.DatabaseInfo;
import org.sleuthkit.autopsy.contentviewers.EdbExplorerTool.EdbExplorerException;
import org.sleuthkit.autopsy.contentviewers.EdbExplorerTool.TableInfo;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file content viewer for Microsoft Extensible Storage Engine (ESE / JET
 * Blue) databases: ntds.dit, SRUDB.dat, WebCacheV01.dat, Windows Search
 * Windows.edb, Exchange mailbox .edb files and the like.
 *
 * Parsing is delegated to the EDB Explorer command line tool (see
 * EdbExplorerTool), which must be installed separately. When the tool cannot
 * be found the viewer explains how to install it and offers to locate it.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class ESEDatabaseViewer extends JPanel implements FileTypeViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ESEDatabaseViewer.class.getName());

    /**
     * MIME type assigned by CustomFileTypesManager to files that start with
     * the ESE header magic (0x89ABCDEF, little endian, at offset 4).
     */
    static final String[] SUPPORTED_MIMETYPES = new String[]{"application/x.ms-ese"}; //NON-NLS
    private static final byte[] ESE_MAGIC = new byte[]{(byte) 0xEF, (byte) 0xCD, (byte) 0xAB, (byte) 0x89};
    private static final int ESE_MAGIC_OFFSET = 4;

    private static final int ROWS_PER_PAGE = 100;
    private static final String TABLE_CARD = "table"; //NON-NLS
    private static final String MESSAGE_CARD = "message"; //NON-NLS

    private final JComboBox<TableInfo> tablesComboBox = new JComboBox<>();
    private final JLabel databaseLabel = new JLabel();
    private final JLabel rowsLabel = new JLabel();
    private final JButton prevPageButton = new JButton();
    private final JButton nextPageButton = new JButton();
    private final JButton exportCsvButton = new JButton();
    private final JPanel contentPanel = new JPanel(new CardLayout());
    private final SQLiteTableView tableView = new SQLiteTableView();
    private final JTextArea messageArea = new JTextArea();
    private final JButton locateButton = new JButton();
    private final JFileChooserFactory chooserHelper = new JFileChooserFactory();

    private AbstractFile currentFile;
    private EdbExplorerTool tool;
    private Path databasePath;
    private boolean databasePathIsTemporary;
    private final Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
    private int currentPage;
    private boolean hasNextPage;
    private SwingWorker<?, ?> worker;
    private SwingWorker<?, ?> exportWorker;

    /**
     * Constructs a file content viewer for ESE database files.
     */
    @NbBundle.Messages({
        "ESEDatabaseViewer.tableLabel.text=Table:",
        "ESEDatabaseViewer.prevPageButton.toolTip=Previous page",
        "ESEDatabaseViewer.nextPageButton.toolTip=Next page",
        "ESEDatabaseViewer.exportCsvButton.text=Export to CSV",
        "ESEDatabaseViewer.locateButton.text=Locate edb-explorer..."
    })
    ESEDatabaseViewer() {
        super(new BorderLayout());
        initComponents();
    }

    private void initComponents() {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        headerPanel.add(new JLabel(Bundle.ESEDatabaseViewer_tableLabel_text()));
        tablesComboBox.setPrototypeDisplayValue(new TableInfo("MSysObjectsShadow_xx", null, null)); //NON-NLS
        tablesComboBox.setRenderer(new TableInfoRenderer());
        tablesComboBox.addActionListener(evt -> tableSelected());
        headerPanel.add(tablesComboBox);
        headerPanel.add(Box.createHorizontalStrut(12));
        headerPanel.add(rowsLabel);
        headerPanel.add(Box.createHorizontalStrut(6));

        prevPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); //NON-NLS
        prevPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); //NON-NLS
        prevPageButton.setToolTipText(Bundle.ESEDatabaseViewer_prevPageButton_toolTip());
        prevPageButton.setBorderPainted(false);
        prevPageButton.setContentAreaFilled(false);
        prevPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        prevPageButton.addActionListener(evt -> showPage(currentPage - 1));
        headerPanel.add(prevPageButton);

        nextPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); //NON-NLS
        nextPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); //NON-NLS
        nextPageButton.setToolTipText(Bundle.ESEDatabaseViewer_nextPageButton_toolTip());
        nextPageButton.setBorderPainted(false);
        nextPageButton.setContentAreaFilled(false);
        nextPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        nextPageButton.addActionListener(evt -> showPage(currentPage + 1));
        headerPanel.add(nextPageButton);
        headerPanel.add(Box.createHorizontalStrut(12));

        exportCsvButton.setText(Bundle.ESEDatabaseViewer_exportCsvButton_text());
        exportCsvButton.addActionListener(evt -> exportCsvButtonActionPerformed());
        headerPanel.add(exportCsvButton);
        headerPanel.add(Box.createHorizontalStrut(12));
        headerPanel.add(databaseLabel);
        add(headerPanel, BorderLayout.NORTH);

        contentPanel.add(tableView, TABLE_CARD);

        JPanel messagePanel = new JPanel(new BorderLayout(0, 8));
        messagePanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setFont(new JLabel().getFont());
        messagePanel.add(messageArea, BorderLayout.CENTER);
        locateButton.setText(Bundle.ESEDatabaseViewer_locateButton_text());
        locateButton.addActionListener(evt -> locateButtonActionPerformed());
        JPanel locatePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        locatePanel.add(locateButton);
        messagePanel.add(locatePanel, BorderLayout.SOUTH);
        contentPanel.add(messagePanel, MESSAGE_CARD);
        add(contentPanel, BorderLayout.CENTER);

        resetControls();
    }

    @Override
    public List<String> getSupportedMIMETypes() {
        return Arrays.asList(SUPPORTED_MIMETYPES);
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public boolean isSupported(AbstractFile file) {
        if (file == null || file.getSize() < ESE_MAGIC_OFFSET + ESE_MAGIC.length) {
            return false;
        }
        byte[] header = new byte[ESE_MAGIC_OFFSET + ESE_MAGIC.length];
        try {
            int read = file.read(header, 0, header.length);
            if (read < header.length) {
                return false;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Failed to read the header of '%s' (objId=%d)", file.getName(), file.getId()), ex); //NON-NLS
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(header, ESE_MAGIC_OFFSET, header.length), ESE_MAGIC);
    }

    @Override
    @NbBundle.Messages({
        "ESEDatabaseViewer.message.toolNotFound=The EDB Explorer command line tool (edb-explorer) is needed to view ESE databases, "
        + "but it was not found on this computer.\n\n"
        + "Install it from https://github.com/keyuraghao/EDB_FILE_EXPLORER (or 'pip install edb-explorer'), "
        + "or click the button below to select the edb-explorer executable if it is installed somewhere else."
    })
    public void setFile(AbstractFile file) {
        cancelWorker();
        cancelExportWorker();
        resetComponent();
        if (file == null) {
            return;
        }
        currentFile = file;

        Path tempDirectory;
        try {
            tempDirectory = Paths.get(Case.getCurrentCaseThrows().getTempDirectory());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No open case while displaying an ESE database", ex); //NON-NLS
            showMessage(Bundle.ESEDatabaseViewer_message_noCurrentCase(), false);
            return;
        }

        Optional<EdbExplorerTool> foundTool = EdbExplorerTool.find(tempDirectory);
        if (!foundTool.isPresent()) {
            showMessage(Bundle.ESEDatabaseViewer_message_toolNotFound(), true);
            return;
        }
        tool = foundTool.get();
        openDatabase(file, tempDirectory);
    }

    @Override
    public void resetComponent() {
        cancelWorker();
        cancelExportWorker();
        deleteTemporaryDatabase();
        currentFile = null;
        tool = null;
        databasePath = null;
        columnsByTable.clear();
        currentPage = 0;
        hasNextPage = false;
        resetControls();
        tableView.setupTable(Collections.emptyList());
        showCard(TABLE_CARD);
    }

    private void resetControls() {
        tablesComboBox.removeAllItems();
        tablesComboBox.setEnabled(false);
        databaseLabel.setText("");
        databaseLabel.setToolTipText(null);
        rowsLabel.setText("");
        prevPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        exportCsvButton.setEnabled(false);
    }

    private void cancelWorker() {
        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }
    }

    private void cancelExportWorker() {
        if (exportWorker != null) {
            exportWorker.cancel(true);
            exportWorker = null;
        }
    }

    private void deleteTemporaryDatabase() {
        if (databasePath != null && databasePathIsTemporary) {
            deleteQuietly(databasePath);
        }
        databasePathIsTemporary = false;
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, String.format("Failed to delete %s", file), ex); //NON-NLS
        }
    }

    private void showCard(String card) {
        ((CardLayout) contentPanel.getLayout()).show(contentPanel, card);
    }

    private void showMessage(String message, boolean offerToLocateTool) {
        messageArea.setText(message);
        locateButton.setVisible(offerToLocateTool);
        showCard(MESSAGE_CARD);
    }

    private void setWaitCursor(boolean waiting) {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(waiting ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /**
     * Makes the database available on the local file system and lists its
     * tables, in the background.
     */
    @NbBundle.Messages({
        "ESEDatabaseViewer.message.noCurrentCase=The case has been closed.",
        "ESEDatabaseViewer.message.failedToExtractFile=The file could not be extracted from the data source.",
        "# {0} - error message",
        "ESEDatabaseViewer.message.failedToOpen=The database could not be read: {0}",
        "ESEDatabaseViewer.message.noTables=No tables found",
        "# {0} - format name", "# {1} - profile name", "# {2} - shutdown state",
        "ESEDatabaseViewer.databaseLabel.text={0} - {1} - {2}",
        "# {0} - format name", "# {1} - shutdown state",
        "ESEDatabaseViewer.databaseLabel.noProfile.text={0} - {1}",
        "# {0} - executable path", "# {1} - version",
        "ESEDatabaseViewer.databaseLabel.toolTip=Parsed by {0} ({1})"
    })
    private void openDatabase(final AbstractFile file, final Path tempDirectory) {
        final EdbExplorerTool openTool = tool;
        setWaitCursor(true);
        worker = new SwingWorker<OpenResult, Void>() {
            /**
             * Temporary copy made by this attempt, if any. Each attempt gets
             * its own copy so that a cancelled or failed attempt can delete
             * it without touching the copy of a newer attempt, and so that a
             * partially written copy is never reused.
             */
            private volatile Path temporaryCopy;

            @Override
            protected OpenResult doInBackground() throws Exception {
                ProcessTerminator terminator = this::isCancelled;
                Path path;
                String localPath = file.getLocalAbsPath();
                if (StringUtils.isNotBlank(localPath) && Files.isRegularFile(Paths.get(localPath))) {
                    // Logical files are already on disk: no need to copy them.
                    path = Paths.get(localPath);
                } else {
                    Files.createDirectories(tempDirectory);
                    path = Files.createTempFile(tempDirectory, String.format("ese-viewer-%d-", file.getId()), "-" + FilenameUtils.getName(file.getName())); //NON-NLS
                    temporaryCopy = path;
                    try {
                        ContentUtils.writeToFile(file, path.toFile());
                    } catch (IOException ex) {
                        throw new EdbExplorerException(Bundle.ESEDatabaseViewer_message_failedToExtractFile(), ex);
                    }
                }
                if (isCancelled()) {
                    return null;
                }
                String version = openTool.getVersion(terminator);
                DatabaseInfo info = openTool.getDatabaseInfo(path, terminator);
                List<TableInfo> tables = openTool.listTables(path, terminator);
                return new OpenResult(path, temporaryCopy != null, version, info, tables);
            }

            @Override
            protected void done() {
                setWaitCursor(false);
                if (isCancelled() || worker != this) {
                    // Superseded by a newer attempt or a reset: this attempt's copy is not needed.
                    deleteQuietly(temporaryCopy);
                    return;
                }
                try {
                    OpenResult result = get();
                    if (result == null) {
                        deleteQuietly(temporaryCopy);
                        return;
                    }
                    databasePath = result.path;
                    databasePathIsTemporary = result.temporary;
                    DatabaseInfo info = result.info;
                    databaseLabel.setText(StringUtils.isBlank(info.getProfileName())
                            ? Bundle.ESEDatabaseViewer_databaseLabel_noProfile_text(info.getKindName(), info.getState())
                            : Bundle.ESEDatabaseViewer_databaseLabel_text(info.getKindName(), info.getProfileName(), info.getState()));
                    databaseLabel.setToolTipText(Bundle.ESEDatabaseViewer_databaseLabel_toolTip(openTool.getExecutable().toString(), result.version));
                    tablesComboBox.removeAllItems();
                    if (result.tables.isEmpty()) {
                        tablesComboBox.setEnabled(false);
                        tablesComboBox.addItem(new TableInfo(Bundle.ESEDatabaseViewer_message_noTables(), null, null));
                    } else {
                        // Enable first: adding the first item selects it, which shows its first page.
                        tablesComboBox.setEnabled(true);
                        result.tables.forEach(tablesComboBox::addItem);
                    }
                    showCard(TABLE_CARD);
                } catch (CancellationException ex) {
                    // The viewer was reset while the database was being opened.
                    deleteQuietly(temporaryCopy);
                } catch (InterruptedException | ExecutionException ex) {
                    deleteQuietly(temporaryCopy);
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    logger.log(Level.WARNING, String.format("Failed to open ESE database '%s' (objId=%d)", file.getName(), file.getId()), cause); //NON-NLS
                    showMessage(Bundle.ESEDatabaseViewer_message_failedToOpen(cause.getMessage()), false);
                }
            }
        };
        worker.execute();
    }

    private void tableSelected() {
        TableInfo table = (TableInfo) tablesComboBox.getSelectedItem();
        if (table == null || !tablesComboBox.isEnabled() || databasePath == null) {
            return;
        }
        showPage(1);
    }

    /**
     * Reads one page of the selected table in the background and shows it.
     *
     * @param page 1-based page number.
     */
    @NbBundle.Messages({
        "# {0} - table name", "# {1} - error message",
        "ESEDatabaseViewer.message.failedToReadTable=Table {0} could not be read: {1}",
        "# {0} - first row", "# {1} - last row",
        "ESEDatabaseViewer.rowsLabel.text=Rows {0} - {1}",
        "ESEDatabaseViewer.rowsLabel.empty.text=No rows"
    })
    private void showPage(final int page) {
        final TableInfo table = (TableInfo) tablesComboBox.getSelectedItem();
        if (table == null || databasePath == null || tool == null) {
            return;
        }
        cancelWorker();
        setWaitCursor(true);
        prevPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        final Path path = databasePath;
        final EdbExplorerTool pageTool = tool;
        final String tableName = table.getName();
        final List<String> cachedColumns = columnsByTable.get(tableName);
        final int offset = (page - 1) * ROWS_PER_PAGE;
        worker = new SwingWorker<PageResult, Void>() {
            @Override
            protected PageResult doInBackground() throws Exception {
                ProcessTerminator terminator = this::isCancelled;
                List<String> columns = cachedColumns != null ? cachedColumns : pageTool.listColumns(path, tableName, terminator);
                // Ask for one row more than a page to find out whether there is a next page.
                List<Map<String, Object>> rows = pageTool.readRows(path, tableName, columns, offset, ROWS_PER_PAGE + 1, terminator);
                boolean more = rows.size() > ROWS_PER_PAGE;
                if (more) {
                    rows = new ArrayList<>(rows.subList(0, ROWS_PER_PAGE));
                }
                return new PageResult(columns, rows, more);
            }

            @Override
            protected void done() {
                setWaitCursor(false);
                if (isCancelled() || worker != this) {
                    // A newer page request or a reset replaced this worker.
                    return;
                }
                try {
                    PageResult result = get();
                    columnsByTable.put(tableName, result.columns);
                    currentPage = page;
                    hasNextPage = result.more;
                    if (result.rows.isEmpty()) {
                        // Show the column headers even when the table (or page) is empty.
                        Map<String, Object> emptyRow = new LinkedHashMap<>();
                        result.columns.forEach(column -> emptyRow.put(column, ""));
                        tableView.setupTable(emptyRow.isEmpty() ? Collections.emptyList() : Collections.singletonList(emptyRow));
                        rowsLabel.setText(Bundle.ESEDatabaseViewer_rowsLabel_empty_text());
                    } else {
                        tableView.setupTable(result.rows);
                        rowsLabel.setText(Bundle.ESEDatabaseViewer_rowsLabel_text(Integer.toString(offset + 1), Integer.toString(offset + result.rows.size())));
                    }
                    prevPageButton.setEnabled(currentPage > 1);
                    nextPageButton.setEnabled(hasNextPage);
                    exportCsvButton.setEnabled(!result.rows.isEmpty() || currentPage > 1);
                    showCard(TABLE_CARD);
                } catch (CancellationException ex) {
                    // The viewer was reset while the page was being read.
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    logger.log(Level.WARNING, String.format("Failed to read table %s of ESE database %s", tableName, path), cause); //NON-NLS
                    MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_message_failedToReadTable(tableName, cause.getMessage()));
                    prevPageButton.setEnabled(currentPage > 1);
                    nextPageButton.setEnabled(hasNextPage);
                }
            }
        };
        worker.execute();
    }

    /**
     * Lets the user pick the edb-explorer executable, remembers it and
     * retries the current file.
     */
    @NbBundle.Messages({
        "ESEDatabaseViewer.locate.title=Select the edb-explorer executable",
        "ESEDatabaseViewer.locate.notExecutable=The selected file is not an executable program."
    })
    private void locateButtonActionPerformed() {
        JFileChooser fileChooser = chooserHelper.getChooser();
        fileChooser.setDialogTitle(Bundle.ESEDatabaseViewer_locate_title());
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = fileChooser.getSelectedFile().toPath();
        if (!Files.isRegularFile(selected) || !Files.isExecutable(selected)) {
            MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_locate_notExecutable());
            return;
        }
        EdbExplorerTool.setConfiguredExecutable(selected);
        AbstractFile file = currentFile;
        setFile(file);
    }

    /**
     * Exports the selected table to a CSV file chosen by the user (the case
     * export directory by default).
     */
    @NbBundle.Messages({
        "ESEDatabaseViewer.csvExport.title=Export to csv file",
        "ESEDatabaseViewer.csvExport.confirm.msg=Do you want to overwrite the existing file?",
        "# {0} - table name", "# {1} - error message",
        "ESEDatabaseViewer.csvExport.errText=Failed to export table {0} to CSV: {1}"
    })
    private void exportCsvButtonActionPerformed() {
        final TableInfo table = (TableInfo) tablesComboBox.getSelectedItem();
        if (table == null || databasePath == null || tool == null) {
            return;
        }
        File exportDirectory;
        try {
            exportDirectory = new File(Case.getCurrentCaseThrows().getExportDirectory());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No open case while exporting an ESE table", ex); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_message_noCurrentCase());
            return;
        }
        JFileChooser fileChooser = chooserHelper.getChooser();
        fileChooser.setDialogTitle(Bundle.ESEDatabaseViewer_csvExport_title());
        fileChooser.setDragEnabled(false);
        fileChooser.setCurrentDirectory(exportDirectory);
        FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("*.csv", "csv"); //NON-NLS
        fileChooser.addChoosableFileFilter(csvFilter);
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setFileFilter(csvFilter);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setSelectedFile(new File(exportDirectory, table.getName() + ".csv")); //NON-NLS
        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = fileChooser.getSelectedFile();
        if (!"csv".equalsIgnoreCase(FilenameUtils.getExtension(selected.getName()))) { //NON-NLS
            selected = new File(selected.getPath() + ".csv"); //NON-NLS
        }
        if (selected.exists() && JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(this,
                Bundle.ESEDatabaseViewer_csvExport_confirm_msg(),
                Bundle.ESEDatabaseViewer_csvExport_title(),
                JOptionPane.YES_NO_OPTION)) {
            return;
        }
        final Path outputFile = selected.toPath();
        final Path path = databasePath;
        final String tableName = table.getName();
        final EdbExplorerTool exportTool = tool;
        cancelExportWorker();
        exportCsvButton.setEnabled(false);
        exportWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Export next to the destination and replace it only once the
                // export succeeded, so a failed export leaves any existing file intact.
                Path directory = outputFile.toAbsolutePath().getParent();
                Path partialFile = Files.createTempFile(directory, outputFile.getFileName().toString() + ".", ".part"); //NON-NLS
                try {
                    exportTool.exportTableToCsv(path, tableName, partialFile, this::isCancelled);
                    if (isCancelled()) {
                        return null;
                    }
                    Files.move(partialFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    deleteQuietly(partialFile);
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled() || exportWorker != this) {
                    // The viewer was reset or a newer export replaced this one.
                    return;
                }
                exportWorker = null;
                exportCsvButton.setEnabled(true);
                try {
                    get();
                } catch (CancellationException ex) {
                    // Cancelled by a reset; nothing to report.
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    logger.log(Level.WARNING, String.format("Failed to export table %s of ESE database %s to %s", tableName, path, outputFile), cause); //NON-NLS
                    MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_csvExport_errText(tableName, cause.getMessage()));
                }
            }
        };
        exportWorker.execute();
    }

    /**
     * Shows the friendly name of a table in the drop-down and its description
     * as the tool tip.
     */
    private static final class TableInfoRenderer extends javax.swing.DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof TableInfo) {
                TableInfo table = (TableInfo) value;
                setText(table.getDisplayName());
                setToolTipText(StringUtils.isBlank(table.getDescription()) ? null : table.getDescription());
            }
            return this;
        }
    }

    /**
     * What the open worker hands back to the UI thread.
     */
    private static final class OpenResult {

        private final Path path;
        private final boolean temporary;
        private final String version;
        private final DatabaseInfo info;
        private final List<TableInfo> tables;

        OpenResult(Path path, boolean temporary, String version, DatabaseInfo info, List<TableInfo> tables) {
            this.path = path;
            this.temporary = temporary;
            this.version = version;
            this.info = info;
            this.tables = tables;
        }
    }

    /**
     * What the page worker hands back to the UI thread.
     */
    private static final class PageResult {

        private final List<String> columns;
        private final List<Map<String, Object>> rows;
        private final boolean more;

        PageResult(List<String> columns, List<Map<String, Object>> rows, boolean more) {
            this.columns = columns;
            this.rows = rows;
            this.more = more;
        }
    }
}
