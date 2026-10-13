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
package org.sleuthkit.autopsy.coreutils.esedb;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only access to a Microsoft Extensible Storage Engine (ESE, also known
 * as JET Blue) database file: ntds.dit, SRUDB.dat, WebCacheV01.dat, Windows
 * Search Windows.edb, Exchange mailbox stores, Windows.edb and others.
 *
 * The implementation follows the on-disk format documented by the Microsoft
 * Extensible-Storage-Engine sources and the libesedb project. It reads the
 * catalog (MSysObjects) to discover tables and columns, then walks each
 * table's B+tree to produce records. Long values, multi-values and the
 * 7-bit and LZXPRESS compression schemes are supported; indexes are not
 * read (records are returned in primary key order).
 *
 * Usage:
 * <pre>
 * try (EseDatabase db = EseDatabase.open(path)) {
 *     for (EseTable table : db.getTables()) {
 *         Iterator&lt;EseRecord&gt; records = table.records();
 *         ...
 *     }
 * }
 * </pre>
 */
public final class EseDatabase implements AutoCloseable {

    /**
     * Header magic ("ulMagic") stored little endian at offset 4 of the file.
     */
    public static final int MAGIC = 0x89ABCDEF;
    public static final int MAGIC_OFFSET = 4;

    private static final int HEADER_SIZE = 668;
    private static final int CATALOG_ROOT_PAGE = 4;
    private static final int SYSOBJ_TABLE = 1;
    private static final int SYSOBJ_COLUMN = 2;
    private static final int SYSOBJ_LONG_VALUE = 4;
    private static final int PAGE_CACHE_SIZE = 2048;

    private final RandomAccessFile file;
    private final long fileSize;
    private final int pageSize;
    private final int formatVersion;
    private final int formatRevision;
    private final int state;
    private final Map<String, EseTable> tables = new LinkedHashMap<>();
    private final Map<Integer, Page> pageCache = new LinkedHashMap<Integer, Page>(PAGE_CACHE_SIZE, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Page> eldest) {
            return size() > PAGE_CACHE_SIZE;
        }
    };

    private EseDatabase(RandomAccessFile file, byte[] header) throws EseException, IOException {
        this.file = file;
        this.fileSize = file.length();
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(MAGIC_OFFSET) != MAGIC) {
            throw new EseException("Not an ESE database (bad header magic)");
        }
        this.formatVersion = buffer.getInt(8);
        this.state = buffer.getInt(52);
        this.formatRevision = buffer.getInt(232);
        int size = buffer.getInt(236);
        this.pageSize = size == 0 ? 4096 : size;
        if (pageSize < 1024 || pageSize > 65536 || Integer.bitCount(pageSize) != 1) {
            throw new EseException(String.format("Unsupported page size %d", pageSize));
        }
        if (formatRevision < 9) {
            throw new EseException(String.format("Unsupported format revision %d", formatRevision));
        }
        readCatalog();
    }

    /**
     * Opens a database file. The file is only ever read.
     *
     * @throws EseException If the file is not an ESE database, uses an
     *                      unsupported format, or its catalog cannot be read.
     */
    public static EseDatabase open(Path path) throws EseException {
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(path.toFile(), "r"); //NON-NLS
            byte[] header = new byte[HEADER_SIZE];
            file.readFully(header);
            return new EseDatabase(file, header);
        } catch (IOException | EseException | RuntimeException ex) {
            // Whatever went wrong (I/O, a format error, or an unexpected failure while decoding a corrupt catalog), do not leak the file.
            if (file != null) {
                try {
                    file.close();
                } catch (IOException closeEx) {
                    ex.addSuppressed(closeEx);
                }
            }
            if (ex instanceof EseException) {
                throw (EseException) ex;
            }
            if (ex instanceof RuntimeException) {
                throw new EseException(String.format("The catalog of %s could not be decoded (corrupt database?)", path), ex);
            }
            throw new EseException(String.format("Could not read %s", path), ex);
        }
    }

    /**
     * @return True if the first bytes of a file carry the ESE header magic.
     */
    public static boolean hasMagic(byte[] header) {
        return header.length >= MAGIC_OFFSET + 4
                && (header[MAGIC_OFFSET] & 0xFF) == 0xEF
                && (header[MAGIC_OFFSET + 1] & 0xFF) == 0xCD
                && (header[MAGIC_OFFSET + 2] & 0xFF) == 0xAB
                && (header[MAGIC_OFFSET + 3] & 0xFF) == 0x89;
    }

    @Override
    public void close() throws EseException {
        try {
            file.close();
        } catch (IOException ex) {
            throw new EseException("Could not close the database file", ex);
        } finally {
            synchronized (pageCache) {
                pageCache.clear();
            }
        }
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * @return The file format version (0x620 for every database since
     *         Windows 2000).
     */
    public int getFormatVersion() {
        return formatVersion;
    }

    /**
     * @return The file format revision ("ulDaeUpdateMajor").
     */
    public int getFormatRevision() {
        return formatRevision;
    }

    /**
     * @return The database state: 1 just created, 2 dirty shutdown, 3 clean
     *         shutdown, 4 being converted, 5 force detach.
     */
    public int getState() {
        return state;
    }

    /**
     * @return A description of the database state, e.g. "Dirty shutdown".
     */
    public String getStateDescription() {
        switch (state) {
            case 1:
                return "Just created"; //NON-NLS
            case 2:
                return "Dirty shutdown"; //NON-NLS
            case 3:
                return "Clean shutdown"; //NON-NLS
            case 4:
                return "Being converted"; //NON-NLS
            case 5:
                return "Force detach"; //NON-NLS
            default:
                return String.format("Unknown state %d", state); //NON-NLS
        }
    }

    boolean hasSmallPages() {
        return pageSize <= 8192;
    }

    /**
     * @return Every table of the database, system tables included, in
     *         catalog order.
     */
    public List<EseTable> getTables() {
        return Collections.unmodifiableList(new ArrayList<>(tables.values()));
    }

    public Optional<EseTable> getTable(String name) {
        return Optional.ofNullable(tables.get(name));
    }

    /**
     * Reads a logical page. Pages are cached.
     */
    Page getPage(int number) throws EseException {
        synchronized (pageCache) {
            Page page = pageCache.get(number);
            if (page != null) {
                return page;
            }
        }
        if (number < 1) {
            throw new EseException(String.format("Invalid page number %d", number));
        }
        long offset = (long) (number + 1) * pageSize;
        if (offset + pageSize > fileSize) {
            throw new EseException(String.format("Page %d lies beyond the end of the file", number));
        }
        byte[] data = new byte[pageSize];
        try {
            synchronized (file) {
                file.seek(offset);
                file.readFully(data);
            }
        } catch (IOException ex) {
            throw new EseException(String.format("Could not read page %d", number), ex);
        }
        Page page = new Page(number, data, hasSmallPages());
        synchronized (pageCache) {
            pageCache.put(number, page);
        }
        return page;
    }

    /**
     * Walks the B+tree below a root page and returns its leaf nodes in key
     * order. Branches are traversed depth first; if the last leaf page
     * reached that way still has a next page (seen in some dirty databases)
     * the chain of next pages is followed too.
     */
    Iterator<Page.Node> leafNodes(int rootPage) throws EseException {
        return new LeafNodeIterator(getPage(rootPage));
    }

    private final class LeafNodeIterator implements Iterator<Page.Node> {

        private final Deque<Iterator<Page.Node>> stack = new ArrayDeque<>();
        private final Set<Integer> visited = new HashSet<>();
        private final Page root;
        private Page lastLeafPage;
        private Page.Node next;
        private boolean followingNextPages;

        LeafNodeIterator(Page root) throws EseException {
            this.root = root;
            push(root);
            advance();
        }

        private void push(Page page) throws EseException {
            if (!visited.add(page.getNumber())) {
                throw new EseException(String.format("Page %d is referenced twice in the tree below page %d (corrupt database?)", page.getNumber(), root.getNumber()));
            }
            if (page.isLeaf()) {
                lastLeafPage = page;
            }
            stack.push(page.getNodes().iterator());
        }

        private void advance() {
            next = null;
            try {
                while (!stack.isEmpty()) {
                    Iterator<Page.Node> nodes = stack.peek();
                    if (!nodes.hasNext()) {
                        stack.pop();
                        continue;
                    }
                    Page.Node node = nodes.next();
                    if (node.getPage().isLeaf()) {
                        next = node;
                        return;
                    }
                    push(getPage(node.getChildPage()));
                }
                // Depth first traversal exhausted: follow next page pointers from the last leaf page.
                if (lastLeafPage != null && lastLeafPage.getNextPage() != 0 && !visited.contains(lastLeafPage.getNextPage()) && (root.isRoot() || followingNextPages)) {
                    followingNextPages = true;
                    Page page = getPage(lastLeafPage.getNextPage());
                    if (page.isLeaf()) {
                        push(page);
                        advance();
                    }
                }
            } catch (EseException ex) {
                throw new EseTable.UncheckedEseException(ex);
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Page.Node next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            Page.Node result = next;
            advance();
            return result;
        }
    }

    /**
     * A position in a B+tree: a leaf page and a node index on it.
     */
    final class Cursor {

        private Page page;
        private int nodeIndex;

        Cursor(Page page, int nodeIndex) {
            this.page = page;
            this.nodeIndex = nodeIndex;
        }

        Page.Node getNode() throws EseException {
            return page.getNode(nodeIndex);
        }

        /**
         * Moves to the next node, crossing to the next page if needed.
         *
         * @return False at the end of the tree.
         */
        boolean moveNext() throws EseException {
            if (nodeIndex + 1 < page.getNodeCount()) {
                nodeIndex++;
                return true;
            }
            int nextPage = page.getNextPage();
            if (nextPage == 0 || nextPage == page.getNumber()) {
                return false;
            }
            page = getPage(nextPage);
            nodeIndex = 0;
            return page.getNodeCount() > 0;
        }
    }

    /**
     * Searches a B+tree for a key and returns a cursor on the matching leaf
     * node, or on the last node that is less than the key.
     */
    Cursor search(int rootPage, byte[] key) throws EseException {
        Page page = getPage(rootPage);
        Set<Integer> visited = new HashSet<>();
        while (true) {
            if (!visited.add(page.getNumber())) {
                throw new EseException(String.format("Page %d is referenced twice while searching below page %d", page.getNumber(), rootPage));
            }
            if (page.getNodeCount() == 0) {
                throw new EseException(String.format("Page %d has no nodes", page.getNumber()));
            }
            int index = findNode(page, key);
            if (page.isBranch()) {
                page = getPage(page.getNode(index).getChildPage());
            } else {
                return new Cursor(page, index);
            }
        }
    }

    /**
     * Binary search of one page. Keys on branch pages are non-inclusive upper
     * bounds, so an exact match on a branch page moves to the next node.
     */
    private static int findNode(Page page, byte[] key) throws EseException {
        int first = 0;
        int last = page.getNodeCount() - 1;
        while (first < last) {
            int middle = (first + last) / 2;
            int comparison = Page.compareKeys(key, page.getNode(middle).getKey());
            if (comparison < 0) {
                last = middle;
            } else if (comparison == 0) {
                if (page.isBranch()) {
                    return Math.min(middle + 1, page.getNodeCount() - 1);
                }
                return middle;
            } else {
                first = middle + 1;
            }
        }
        return first;
    }

    /**
     * Reads MSysObjects and builds the tables and their columns.
     */
    private void readCatalog() throws EseException {
        EseTable catalog = new EseTable(this, 2, "MSysObjects", CATALOG_ROOT_PAGE, null); //NON-NLS
        for (EseColumn column : catalogColumns()) {
            catalog.addColumn(column);
        }
        catalog.finishColumns();

        EseColumn typeColumn = catalog.getColumn("Type").get(); //NON-NLS
        EseColumn idColumn = catalog.getColumn("Id").get(); //NON-NLS
        EseColumn objidColumn = catalog.getColumn("ObjidTable").get(); //NON-NLS
        EseColumn pgnoColumn = catalog.getColumn("ColtypOrPgnoFDP").get(); //NON-NLS
        EseColumn spaceColumn = catalog.getColumn("SpaceUsage").get(); //NON-NLS
        EseColumn flagsColumn = catalog.getColumn("Flags").get(); //NON-NLS
        EseColumn localeColumn = catalog.getColumn("PagesOrLocale").get(); //NON-NLS
        EseColumn nameColumn = catalog.getColumn("Name").get(); //NON-NLS
        EseColumn templateColumn = catalog.getColumn("TemplateTable").get(); //NON-NLS
        EseColumn defaultColumn = catalog.getColumn("DefaultValue").get(); //NON-NLS

        Map<Integer, EseTable> tablesByObjectId = new LinkedHashMap<>();
        Iterator<EseRecord> records = catalog.records();
        try {
            while (records.hasNext()) {
                EseRecord record = records.next();
                Integer type = (Integer) record.getValue(typeColumn);
                Integer objectId = (Integer) record.getValue(objidColumn);
                if (type == null || objectId == null) {
                    continue;
                }
                if (type == SYSOBJ_TABLE) {
                    String name = (String) record.getValue(nameColumn);
                    Integer rootPage = (Integer) record.getValue(pgnoColumn);
                    if (name == null || rootPage == null) {
                        continue;
                    }
                    EseTable table = new EseTable(this, objectId, name, rootPage, (String) record.getValue(templateColumn));
                    tablesByObjectId.put(objectId, table);
                    tables.put(name, table);
                } else if (type == SYSOBJ_COLUMN) {
                    EseTable table = tablesByObjectId.get(objectId);
                    Integer id = (Integer) record.getValue(idColumn);
                    String name = (String) record.getValue(nameColumn);
                    Integer coltyp = (Integer) record.getValue(pgnoColumn);
                    if (table == null || id == null || name == null || coltyp == null) {
                        continue;
                    }
                    Integer size = (Integer) record.getValue(spaceColumn);
                    Integer flags = (Integer) record.getValue(flagsColumn);
                    Integer codePage = (Integer) record.getValue(localeColumn);
                    Object defaultValue = record.getValue(defaultColumn);
                    table.addColumn(new EseColumn(id, name, EseColumnType.fromIdentifier(coltyp),
                            size == null ? 0 : size, codePage == null ? 0 : codePage, flags == null ? 0 : flags,
                            defaultValue instanceof byte[] ? (byte[]) defaultValue : null, false));
                } else if (type == SYSOBJ_LONG_VALUE) {
                    EseTable table = tablesByObjectId.get(objectId);
                    Integer rootPage = (Integer) record.getValue(pgnoColumn);
                    if (table != null && rootPage != null) {
                        table.setLongValueRootPage(rootPage);
                    }
                }
            }
        } catch (EseTable.UncheckedEseException ex) {
            throw ex.getCause();
        }

        // Tables derived from a template inherit its columns (and its long value tree).
        for (EseTable table : tables.values()) {
            inheritTemplate(table, new HashSet<>());
        }
        for (EseTable table : tables.values()) {
            table.finishColumns();
        }
    }

    private void inheritTemplate(EseTable table, Set<String> chain) {
        Optional<String> templateName = table.getTemplateName();
        if (!templateName.isPresent() || !chain.add(table.getName())) {
            return;
        }
        EseTable template = tables.get(templateName.get());
        if (template == null) {
            return;
        }
        inheritTemplate(template, chain);
        for (EseColumn column : template.getColumns()) {
            table.addColumn(column.asDerived());
        }
        if (!table.hasLongValueTree() && template.hasLongValueTree()) {
            table.setLongValueRootPage(templateRootPage(template));
        }
    }

    private static int templateRootPage(EseTable template) {
        // Only called when the template has a long value tree.
        return template.longValueRootPageForDerived();
    }

    /**
     * The fixed catalog schema used to bootstrap the parsing of
     * MSysObjects, which describes every other table (including itself).
     */
    private static List<EseColumn> catalogColumns() {
        List<EseColumn> columns = new ArrayList<>();
        columns.add(new EseColumn(1, "ObjidTable", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(2, "Type", EseColumnType.SHORT, 2, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(3, "Id", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(4, "ColtypOrPgnoFDP", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(5, "SpaceUsage", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(6, "Flags", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(7, "PagesOrLocale", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(8, "RootFlag", EseColumnType.BIT, 1, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(9, "RecordOffset", EseColumnType.SHORT, 2, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(10, "LCMapFlags", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(11, "KeyMost", EseColumnType.UNSIGNED_SHORT, 2, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(12, "LVChunkMax", EseColumnType.LONG, 4, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(128, "Name", EseColumnType.TEXT, 0, EseColumn.CODE_PAGE_ASCII, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(129, "Stats", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(130, "TemplateTable", EseColumnType.TEXT, 0, EseColumn.CODE_PAGE_ASCII, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(131, "DefaultValue", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(132, "KeyFldIDs", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(133, "VarSegMac", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(134, "ConditionalColumns", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(135, "TupleLimits", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(136, "Version", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(137, "SortID", EseColumnType.BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(256, "CallbackData", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(257, "CallbackDependencies", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(258, "SeparateLV", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(259, "SpaceHints", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(260, "SpaceDeferredLVHints", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        columns.add(new EseColumn(261, "LocaleName", EseColumnType.LONG_BINARY, 0, 0, 0, null, false)); //NON-NLS
        return columns;
    }
}
