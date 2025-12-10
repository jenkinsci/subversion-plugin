package hudson.scm;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


class SubversionSCMChangeLogEntryTest {

    private final SubversionChangeLogSet.LogEntry entry = SubversionChangeLogUtil.buildChangeLogEntry(99, "Dummy");

    @Test
    void testRemoveIgnoredDirPropChangesNotRemoving() {
        addPathToEntry("A", "file", "a");
        addPathToEntry("A", "dir", "b");
        addPathToEntry("D", "file", "c");
        addPathToEntry("D", "dir", "d");
        addPathToEntry("M", "file", "e");

        int oldSize = entry.getPaths().size();
        entry.removePropertyOnlyPaths();
        assertThat(entry.getPaths().size(), is(oldSize));
    }

    @Test
    void testRemoveIgnoredDirPropChanges() {
        addPathToEntry("A", "file", "filetokeep");
        addPathToEntry("M", "dir", "dirtodelete");
        addPathToEntry("M", "dir", "anotherdirtodelete");
        entry.removePropertyOnlyPaths();
        assertThat(entry.getPaths().size(), is(1));
    }

    private void addPathToEntry(String action, String kind, String path) {
        SubversionChangeLogSet.Path result = new SubversionChangeLogSet.Path();
        result.setAction(action);
        result.setKind(kind);
        result.setValue(path);
        result.setLogEntry(entry);
        entry.addPath(result);
    }

}
