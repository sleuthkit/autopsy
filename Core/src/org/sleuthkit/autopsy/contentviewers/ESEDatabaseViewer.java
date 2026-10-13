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
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.esedb.EseColumn;
import org.sleuthkit.autopsy.coreutils.esedb.EseColumnType;
import org.sleuthkit.autopsy.coreutils.esedb.EseDatabase;
import org.sleuthkit.autopsy.coreutils.esedb.EseDateTime;
import org.sleuthkit.autopsy.coreutils.esedb.EseException;
import org.sleuthkit.autopsy.coreutils.esedb.EseRecord;
import org.sleuthkit.autopsy.coreutils.esedb.EseTable;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A file content viewer for Microsoft Extensible Storage Engine (ESE / JET
 * Blue) databases: ntds.dit, SRUDB.dat, WebCacheV01.dat, Windows Search
 * Windows.edb, Exchange mailbox .edb files and the like. The database is
 * parsed by the built-in EseDatabase reader (org.sleuthkit.autopsy.coreutils.esedb).
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

    private static final int ROWS_PER_PAGE = 100;
    private static final int MAX_BINARY_BYTES_SHOWN = 64;
    private static final String TABLE_CARD = "table"; //NON-NLS
    private static final String MESSAGE_CARD = "message"; //NON-NLS
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC); //NON-NLS

    private final JComboBox<EseTable> tablesComboBox = new JComboBox<>();
    private final JLabel databaseLabel = new JLabel();
    private final JLabel rowsLabel = new JLabel();
    private final JButton prevPageButton = new JButton();
    private final JButton nextPageButton = new JButton();
    private final JButton exportCsvButton = new JButton();
    private final JPanel contentPanel = new JPanel(new CardLayout());
    private final SQLiteTableView tableView = new SQLiteTableView();
    private final JTextArea messageArea = new JTextArea();
    private final JFileChooserFactory chooserHelper = new JFileChooserFactory();

    private EseDatabase database;
    private Path databasePath;
    private boolean databasePathIsTemporary;
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
        "ESEDatabaseViewer.exportCsvButton.text=Export to CSV"
    })
    ESEDatabaseViewer() {
        super(new BorderLayout());
        initComponents();
    }

    private void initComponents() {
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        headerPanel.add(new JLabel(Bundle.ESEDatabaseViewer_tableLabel_text()));
        tablesComboBox.setPrototypeDisplayValue(null);
        tablesComboBox.setRenderer(new TableRenderer());
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

        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setOpaque(false);
        messageArea.setFont(new JLabel().getFont());
        messagePanel.add(messageArea, BorderLayout.CENTER);
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
        if (file == null || file.getSize() < EseDatabase.MAGIC_OFFSET + 4) {
            return false;
        }
        byte[] header = new byte[EseDatabase.MAGIC_OFFSET + 4];
        try {
            if (file.read(header, 0, header.length) < header.length) {
                return false;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Failed to read the header of '%s' (objId=%d)", file.getName(), file.getId()), ex); //NON-NLS
            return false;
        }
        return EseDatabase.hasMagic(header);
    }

    @Override
    public void setFile(AbstractFile file) {
        cancelWorker();
        cancelExportWorker();
        resetComponent();
        if (file == null) {
            return;
        }
        Path tempDirectory;
        try {
            tempDirectory = Paths.get(Case.getCurrentCaseThrows().getTempDirectory());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No open case while displaying an ESE database", ex); //NON-NLS
            showMessage(Bundle.ESEDatabaseViewer_message_noCurrentCase());
            return;
        }
        openDatabase(file, tempDirectory);
    }

    @Override
    public void resetComponent() {
        cancelWorker();
        cancelExportWorker();
        closeDatabase();
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

    private void closeDatabase() {
        if (database != null) {
            try {
                database.close();
            } catch (EseException ex) {
                logger.log(Level.WARNING, String.format("Failed to close ESE database %s", databasePath), ex); //NON-NLS
            }
            database = null;
        }
        if (databasePath != null && databasePathIsTemporary) {
            deleteQuietly(databasePath);
        }
        databasePath = null;
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

    private void showMessage(String message) {
        messageArea.setText(message);
        showCard(MESSAGE_CARD);
    }

    private void setWaitCursor(boolean waiting) {
        WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(waiting ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /**
     * Makes the database available on the local file system, opens it and
     * lists its tables, in the background.
     */
    @NbBundle.Messages({
        "ESEDatabaseViewer.message.noCurrentCase=The case has been closed.",
        "ESEDatabaseViewer.message.failedToExtractFile=The file could not be extracted from the data source.",
        "# {0} - error message",
        "ESEDatabaseViewer.message.failedToOpen=The database could not be read: {0}",
        "ESEDatabaseViewer.message.noTables=No tables found",
        "# {0} - shutdown state", "# {1} - page size", "# {2} - format revision",
        "ESEDatabaseViewer.databaseLabel.text=ESE database - {0} - {1} byte pages - format revision {2}"
    })
    private void openDatabase(final AbstractFile file, final Path tempDirectory) {
        setWaitCursor(true);
        worker = new SwingWorker<OpenResult, Void>() {
            /**
             * Temporary copy made by this attempt, if any. Each attempt gets
             * its own copy so that a cancelled or failed attempt can delete
             * it without touching the copy of a newer attempt.
             */
            private volatile Path temporaryCopy;

            @Override
            protected OpenResult doInBackground() throws Exception {
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
                        throw new EseException(Bundle.ESEDatabaseViewer_message_failedToExtractFile(), ex);
                    }
                }
                if (isCancelled()) {
                    return null;
                }
                EseDatabase db = EseDatabase.open(path);
                List<EseTable> tables = new ArrayList<>();
                for (EseTable table : db.getTables()) {
                    if (!table.isSystemTable()) {
                        tables.add(table);
                    }
                }
                if (tables.isEmpty()) {
                    tables.addAll(db.getTables());
                }
                return new OpenResult(db, path, temporaryCopy != null, tables);
            }

            @Override
            protected void done() {
                setWaitCursor(false);
                if (isCancelled() || worker != this) {
                    // Superseded by a newer attempt or a reset.
                    discard();
                    return;
                }
                try {
                    OpenResult result = get();
                    if (result == null) {
                        discard();
                        return;
                    }
                    database = result.database;
                    databasePath = result.path;
                    databasePathIsTemporary = result.temporary;
                    databaseLabel.setText(Bundle.ESEDatabaseViewer_databaseLabel_text(
                            database.getStateDescription(), Integer.toString(database.getPageSize()), Integer.toString(database.getFormatRevision())));
                    tablesComboBox.removeAllItems();
                    if (result.tables.isEmpty()) {
                        tablesComboBox.setEnabled(false);
                        rowsLabel.setText(Bundle.ESEDatabaseViewer_message_noTables());
                    } else {
                        // Enable first: adding the first item selects it, which shows its first page.
                        tablesComboBox.setEnabled(true);
                        result.tables.forEach(tablesComboBox::addItem);
                    }
                    showCard(TABLE_CARD);
                } catch (CancellationException ex) {
                    discard();
                } catch (InterruptedException | ExecutionException ex) {
                    discard();
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    logger.log(Level.WARNING, String.format("Failed to open ESE database '%s' (objId=%d)", file.getName(), file.getId()), cause); //NON-NLS
                    showMessage(Bundle.ESEDatabaseViewer_message_failedToOpen(cause.getMessage()));
                }
            }

            /**
             * Releases what this attempt produced when its result is not
             * used.
             */
            private void discard() {
                try {
                    OpenResult result = isDone() && !isCancelled() ? get() : null;
                    if (result != null && result.database != null) {
                        result.database.close();
                    }
                } catch (InterruptedException | ExecutionException | CancellationException | EseException ex) {
                    // Nothing to release.
                }
                deleteQuietly(temporaryCopy);
            }
        };
        worker.execute();
    }

    private void tableSelected() {
        EseTable table = (EseTable) tablesComboBox.getSelectedItem();
        if (table == null || !tablesComboBox.isEnabled() || database == null) {
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
        final EseTable table = (EseTable) tablesComboBox.getSelectedItem();
        if (table == null || database == null) {
            return;
        }
        cancelWorker();
        setWaitCursor(true);
        prevPageButton.setEnabled(false);
        nextPageButton.setEnabled(false);
        final int offset = (page - 1) * ROWS_PER_PAGE;
        worker = new SwingWorker<PageResult, Void>() {
            @Override
            protected PageResult doInBackground() throws Exception {
                List<Map<String, Object>> rows = new ArrayList<>();
                Iterator<EseRecord> records = table.records();
                int index = 0;
                try {
                    // Ask for one row more than a page to find out whether there is a next page.
                    while (records.hasNext() && rows.size() <= ROWS_PER_PAGE) {
                        if (isCancelled()) {
                            return null;
                        }
                        EseRecord record = records.next();
                        if (index++ < offset) {
                            continue;
                        }
                        rows.add(displayValues(record));
                    }
                } catch (EseTable.UncheckedEseException ex) {
                    throw ex.getCause();
                }
                boolean more = rows.size() > ROWS_PER_PAGE;
                if (more) {
                    rows = new ArrayList<>(rows.subList(0, ROWS_PER_PAGE));
                }
                return new PageResult(rows, more);
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
                    if (result == null) {
                        return;
                    }
                    currentPage = page;
                    hasNextPage = result.more;
                    if (result.rows.isEmpty()) {
                        // Show the column headers even when the table (or page) is empty.
                        Map<String, Object> emptyRow = new LinkedHashMap<>();
                        table.getColumns().forEach(column -> emptyRow.put(column.getName(), ""));
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
                    logger.log(Level.WARNING, String.format("Failed to read table %s of ESE database %s", table.getName(), databasePath), cause); //NON-NLS
                    MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_message_failedToReadTable(table.getName(), cause.getMessage()));
                    prevPageButton.setEnabled(currentPage > 1);
                    nextPageButton.setEnabled(hasNextPage);
                }
            }
        };
        worker.execute();
    }

    /**
     * Converts a record to the strings shown in the grid, by column name.
     */
    @NbBundle.Messages({
        "# {0} - error message",
        "ESEDatabaseViewer.value.error=!ERROR! {0}",
        "# {0} - number of bytes",
        "ESEDatabaseViewer.value.binaryTruncated=... ({0} bytes)"
    })
    private static Map<String, Object> displayValues(EseRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (EseColumn column : record.getTable().getColumns()) {
            String text;
            try {
                text = displayValue(column, record.getValue(column));
            } catch (EseException | RuntimeException ex) {
                text = Bundle.ESEDatabaseViewer_value_error(ex.getMessage());
            }
            row.put(column.getName(), text);
        }
        return row;
    }

    private static String displayValue(EseColumn column, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object item : (List<?>) value) {
                parts.add(displayValue(column, item));
            }
            return String.join("; ", parts); //NON-NLS
        }
        if (value instanceof byte[]) {
            return hex((byte[]) value, MAX_BINARY_BYTES_SHOWN);
        }
        if (column.getType() == EseColumnType.DATE_TIME && value instanceof Long) {
            return EseDateTime.decode((Long) value).map(TIMESTAMP_FORMAT::format).orElse(value.toString());
        }
        return value.toString();
    }

    private static String hex(byte[] bytes, int maxBytes) {
        StringBuilder sb = new StringBuilder("0x"); //NON-NLS
        int shown = Math.min(bytes.length, maxBytes);
        for (int i = 0; i < shown; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF)); //NON-NLS
        }
        if (bytes.length > shown) {
            sb.append(Bundle.ESEDatabaseViewer_value_binaryTruncated(Integer.toString(bytes.length)));
        }
        return sb.toString();
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
        final EseTable table = (EseTable) tablesComboBox.getSelectedItem();
        if (table == null || database == null) {
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
        cancelExportWorker();
        exportCsvButton.setEnabled(false);
        exportWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Write next to the destination and replace it only once the
                // export succeeded, so a failed export leaves any existing file intact.
                Path directory = outputFile.toAbsolutePath().getParent();
                Path partialFile = Files.createTempFile(directory, outputFile.getFileName().toString() + ".", ".part"); //NON-NLS
                try {
                    writeCsv(table, partialFile, this::isCancelled);
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
                    logger.log(Level.WARNING, String.format("Failed to export table %s of ESE database %s to %s", table.getName(), databasePath, outputFile), cause); //NON-NLS
                    MessageNotifyUtil.Message.error(Bundle.ESEDatabaseViewer_csvExport_errText(table.getName(), cause.getMessage()));
                }
            }
        };
        exportWorker.execute();
    }

    /**
     * Writes every record of a table as CSV (UTF-8, header row, every value
     * quoted, binary values in full as hex).
     */
    private static void writeCsv(EseTable table, Path outputFile, java.util.function.BooleanSupplier cancelled) throws IOException, EseException {
        List<EseColumn> columns = table.getColumns();
        try (BufferedWriter out = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            StringBuilder line = new StringBuilder();
            for (EseColumn column : columns) {
                appendCsvValue(line, column.getName());
            }
            out.write(line.toString());
            out.newLine();
            Iterator<EseRecord> records = table.records();
            try {
                while (records.hasNext()) {
                    if (cancelled.getAsBoolean()) {
                        return;
                    }
                    EseRecord record = records.next();
                    line.setLength(0);
                    for (EseColumn column : columns) {
                        String text;
                        try {
                            Object value = record.getValue(column);
                            text = value instanceof byte[] ? hex((byte[]) value, Integer.MAX_VALUE) : displayValue(column, value);
                        } catch (EseException | RuntimeException ex) {
                            text = Bundle.ESEDatabaseViewer_value_error(ex.getMessage());
                        }
                        appendCsvValue(line, text);
                    }
                    out.write(line.toString());
                    out.newLine();
                }
            } catch (EseTable.UncheckedEseException ex) {
                throw ex.getCause();
            }
        }
    }

    private static void appendCsvValue(StringBuilder line, String value) {
        if (line.length() > 0) {
            line.append(',');
        }
        line.append('"').append(value.replace("\"", "\"\"")).append('"'); //NON-NLS
    }

    /**
     * Shows table names in the drop-down.
     */
    private static final class TableRenderer extends javax.swing.DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof EseTable) {
                setText(((EseTable) value).getName());
            }
            return this;
        }
    }

    /**
     * What the open worker hands back to the UI thread.
     */
    private static final class OpenResult {

        private final EseDatabase database;
        private final Path path;
        private final boolean temporary;
        private final List<EseTable> tables;

        OpenResult(EseDatabase database, Path path, boolean temporary, List<EseTable> tables) {
            this.database = database;
            this.path = path;
            this.temporary = temporary;
            this.tables = tables;
        }
    }

    /**
     * What the page worker hands back to the UI thread.
     */
    private static final class PageResult {

        private final List<Map<String, Object>> rows;
        private final boolean more;

        PageResult(List<Map<String, Object>> rows, boolean more) {
            this.rows = rows;
            this.more = more;
        }
    }
}
