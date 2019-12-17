package jenkins.scm.impl.subversion;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Stephen Connolly
 */
public class SubversionSCMSourceTest {

    @Test
    public void isMatch() {
        assertThat(SubversionSCMSource.isMatch("trunk", "trunk"), is(true));
        assertThat(SubversionSCMSource.isMatch("trunk", "trunk*"), is(true));
        assertThat(SubversionSCMSource.isMatch("trunk", "*trunk"), is(true));
        assertThat(SubversionSCMSource.isMatch("trunk", "tr*nk"), is(true));
        assertThat(SubversionSCMSource.isMatch("trunk", "*"), is(true));
        assertThat(SubversionSCMSource.isMatch("branch", "trunk"), is(false));
        assertThat(SubversionSCMSource.isMatch("branch", "tr*nk"), is(false));
        assertThat(SubversionSCMSource.isMatch("branch", "*trunk"), is(false));
    }

    @Test
    public void splitCludes() {
        assertThat(SubversionSCMSource.splitCludes("trunk"),
                is(new TreeSet<>(Collections.singletonList("trunk"))));
        assertThat(SubversionSCMSource.splitCludes("trunk,branches/*"),
                is(new TreeSet<>(Arrays.asList("trunk", "branches/*"))));
        assertThat(SubversionSCMSource.splitCludes("trunk, branches/*"),
                is(new TreeSet<>(Arrays.asList("trunk", "branches/*"))));
        assertThat(SubversionSCMSource.splitCludes("trunk , , branches/*"),
                is(new TreeSet<>(Arrays.asList("trunk", "branches/*"))));
        assertThat(SubversionSCMSource.splitCludes("trunk , , branches/*   , tags/* "),
                is(new TreeSet<>(Arrays.asList("trunk", "branches/*", "tags/*"))));
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    private static SortedSet<List<String>> pathSet(List<String>... paths) {
        SortedSet<List<String>> result = new TreeSet<>(new SubversionSCMSource.StringListComparator());
        result.addAll(Arrays.asList(paths));
        return result;
    }

    @Test
    public void toPaths() {
        assertThat(SubversionSCMSource.toPaths(SubversionSCMSource.splitCludes("trunk")), is(pathSet(list("trunk"))));
        assertThat(SubversionSCMSource.toPaths(SubversionSCMSource.splitCludes("trunk,branches/*")), is(pathSet(
                list("trunk"), list("branches", "*")
        )));
        assertThat(
                SubversionSCMSource.toPaths(SubversionSCMSource.splitCludes("trunk,branches/*,tags/*,sandbox/*/*")),
                is(pathSet(
                        list("trunk"), list("branches", "*"), list("tags", "*"), list("sandbox", "*", "*")
                )));
    }

    @Test
    public void filterPaths() {
        assertThat(SubversionSCMSource
                .filterPaths(pathSet(list("trunk"), list("branches", "foo"), list("branches", "bar")),
                        list("trunk")),
                is(pathSet(list("trunk"))));
        assertThat(SubversionSCMSource
                .filterPaths(pathSet(list("trunk"), list("branches", "foo"), list("branches", "bar")),
                        list("branches")),
                is(pathSet(list("branches", "foo"), list("branches", "bar"))));
    }

    @Test
    public void groupPaths() {
        SortedMap<List<String>, SortedSet<List<String>>> result;
        SortedSet<List<String>> data = pathSet(
                list("trunk"),
                list("branches", "*"),
                list("tags", "*"),
                list("sandbox", "*")
        );
        result = SubversionSCMSource.groupPaths(data, Collections.emptyList());
        assertThat(result.keySet(),
                is((Set<List<String>>) pathSet(list("trunk"), list("branches"), list("tags"), list("sandbox"))));
        assertThat(result.get(list("trunk")), is(pathSet(list("trunk"))));
        assertThat(result.get(list("branches")), is(pathSet(list("branches", "*"))));
        data = pathSet(
                list("trunk", "foo", "bar"),
                list("trunk", "foo", "bas"),
                list("trunk", "bar", "bas"),
                list("branches", "foo", "bar", "*"),
                list("tags", "*"),
                list("sandbox", "*", "foo", "bar", "*"),
                list("sandbox", "*", "foo", "bas", "*"),
                list("sandbox", "*", "bar", "bas", "*")
        );
        result = SubversionSCMSource.groupPaths(data, Collections.emptyList());
        assertThat(result.keySet(),
                is((Set<List<String>>) pathSet(list("trunk"), list("branches", "foo", "bar"), list("tags"),
                        list("sandbox"))));
        result = SubversionSCMSource.groupPaths(result.get(list("sandbox")), list("sandbox", "*"));
        assertThat(result.keySet(),
                is((Set<List<String>>) pathSet(list("sandbox", "*", "foo"), list("sandbox", "*", "bar", "bas"))));
        data = pathSet(
                list("trunk", "foo", "bar"),
                list("trunk", "foo", "bas"),
                list("trunk", "bar", "bas"),
                list("branches", "foo", "bar", "*"),
                list("tags", "*"),
                list("sandbox", "joe-*", "foo", "bar", "*"),
                list("sandbox", "jim-*", "foo", "bas", "*"),
                list("sandbox", "*", "bar", "bas", "*")
        );
        result = SubversionSCMSource.groupPaths(data, Collections.emptyList());
        assertThat(result.keySet(),
                is((Set<List<String>>) pathSet(list("trunk"), list("branches", "foo", "bar"), list("tags"),
                        list("sandbox"))));
        assertThat(result.get(list("sandbox")),
                is((Set<List<String>>) pathSet(list("sandbox", "joe-*", "foo", "bar", "*"),
                        list("sandbox", "jim-*", "foo", "bas", "*"), list("sandbox", "*", "bar", "bas", "*"))));
        result = SubversionSCMSource.groupPaths(result.get(list("sandbox")), list("sandbox"));
        assertThat(result.keySet(), is((Set<List<String>>) pathSet(list("sandbox"))));
        result = SubversionSCMSource.groupPaths(result.get(list("sandbox")), list("sandbox", "joe-*"));
        assertThat(result.keySet(), is((Set<List<String>>) pathSet(list("sandbox", "joe-*", "foo", "bar"))));
    }

    @Test
    public void startsWith() {
        assertThat(SubversionSCMSource.startsWith(list(), list()), is(true));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list()), is(true));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a")), is(true));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a", "b")), is(true));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a", "b", "c")), is(true));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a", "b", "c", "d")), is(false));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a", "b", "d")), is(false));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("a", "d")), is(false));
        assertThat(SubversionSCMSource.startsWith(list("a", "b", "c"), list("d")), is(false));
    }

    @Test
    public void wildcardStartsWith() {
        assertThat(SubversionSCMSource.wildcardStartsWith(list(), list()), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list()), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "c")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "c")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "c")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "*", "c")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "*", "*")), is(true));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "c", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "c", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "c", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "*", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "c", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "c", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "c", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "*", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "*", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "*", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "*", "*", "*")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "b", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("*", "b", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "*", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("a", "d")), is(false));
        assertThat(SubversionSCMSource.wildcardStartsWith(list("a", "b", "c"), list("d")), is(false));
    }

}
