package pl.sdadas.fsbrowser.view.filebrowser;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import pl.sdadas.fsbrowser.exception.FsAccessException;
import pl.sdadas.fsbrowser.exception.FsException;
import pl.sdadas.fsbrowser.fs.connection.FsConnection;
import pl.sdadas.fsbrowser.fs.connection.FsIterator;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Sławomir Dadas
 */
public class FileSystemTableModel extends AbstractTableModel {

    private FileStatus path;

    private List<FileItem> children = new ArrayList<>();

    private FsConnection connection;

    private FsIterator iterator;

    private FileBrowserColumn [] columns = createColumns();

    private boolean hasMoreRows;

    private AtomicBoolean loading = new AtomicBoolean(false);

    private List<PathListener> pathListeners = new ArrayList<>();

    public FileSystemTableModel(FsConnection connection, String path) throws FsException {
        this.path = connection.status(path);
        this.connection = connection;
        this.hasMoreRows = true;
        loadRows(this.path);
    }

    public void addPathListener(PathListener listener) {
        this.pathListeners.add(listener);
    }

    private void firePathChanged(Path path) {
        for (PathListener listener : this.pathListeners) {
            listener.onPathChanged(path);
        }
    }

    public Path getCurrentPath() {
        return this.path.getPath();
    }

    @Override
    public int getRowCount() {
        return children.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return this.columns[column].getName();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return this.columns[columnIndex].getColumnClass();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if(rowIndex >= children.size()) return null;
        FileItem child = children.get(rowIndex);
        return columns[columnIndex].getColumnProvider().apply(child);
    }

    public void onFileClicked(Path value) throws FsException {
        if(value == null) return;
        FileStatus status = connection.status(value);
        if(status.isFile()) return;
        loading.set(true);
        loadRows(status);
    }

    public void onFileClicked(int idx) throws FsException {
        if(idx < 0 || idx > children.size()) return;
        FileItem parent = children.get(idx);
        FileStatus status = parent.getStatus();
        if(status == null) {
            Path target = path.getPath().getParent();
            status = connection.status(target);
        } else if(status.isFile()) {
            return;
        }
        loading.set(true);
        loadRows(status);
    }

    public void reloadView() throws FsException {
        loading.set(true);
        loadRows(this.path);
    }

    public void loadMoreRows() {
        if(!hasMoreRows || !loading.compareAndSet(false, true)) return;
        fetchRows(100, false);
    }

    public void loadAllRows() {
        if(!hasMoreRows || !loading.compareAndSet(false, true)) return;
        fetchRows(Integer.MAX_VALUE, false);
    }

    private void loadRows(FileStatus parent) throws FsException {
        FsIterator iterator = this.connection.list(parent.getPath());
        this.path = parent;
        this.iterator = iterator;
        this.children.clear();
        if(this.path.getPath().getParent() != null) {
            this.children.add(FileItem.parent());
        }
        this.hasMoreRows = true;
        fetchRows(100, true);
        firePathChanged(this.path.getPath());
    }

    private void fetchRows(int rows, boolean initial) {
        try {
            doFetchRows(rows, initial);
        } finally {
            loading.set(false);
        }
    }

    private void doFetchRows(int rows, boolean initial) {
        if(iterator == null) return;
        int startSize = this.children.size();
        int idx = 0;
        while(iterator.hasNext() && idx < rows) {
            FileStatus file = iterator.next();
            children.add(new FileItem(file));
            idx++;
        }
        if(!iterator.hasNext()) {
            this.hasMoreRows = false;
        }

        if(initial) {
            fireTableDataChanged();
            return;
        }

        int endSize = this.children.size();
        if(endSize != startSize) {
            fireTableRowsInserted(startSize, endSize - 1);
        }
    }

    private FileBrowserColumn[] createColumns() {
        return new FileBrowserColumn[] {
            FileBrowserColumn.create("", Icon.class, FileItem::getIcon).minWidth(20).maxWidth(20),
            FileBrowserColumn.create("Name", FileItem::getName).sortable(true),
            FileBrowserColumn.create("Group", FileItem::getGroup).minWidth(90).maxWidth(120).sortable(true),
            FileBrowserColumn.create("Owner", FileItem::getOwner).minWidth(90).maxWidth(120).sortable(true),
            FileBrowserColumn.create("Permissions", FileItem::getPermissions).minWidth(90).maxWidth(90),
            FileBrowserColumn.create("Modified", FileItem.FileTimestamp.class, FileItem::getModificationTime)
                    .minWidth(150).maxWidth(150).sortable(true),
            FileBrowserColumn.create("Size", FileItem.FileSize.class, FileItem::getSize).minWidth(110).maxWidth(150).sortable(true),
        };
    }

    public boolean hasMoreRows() {
        return this.hasMoreRows;
    }

    public int getColumnIndex(String name) {
        for (int i = 0; i < this.columns.length; i++) {
            if(this.columns[i].getName().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public FileBrowserColumn getColumn(int idx) {
        return this.columns[idx];
    }

    public FileItem getRow(int idx) {
        return this.children.get(idx);
    }
}
