package jenkins.scm.impl.subversion;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;

public class SubversionSCMFileSystemTest {

	@ClassRule
	public static JenkinsRule r = new JenkinsRule();

	@Rule
	public SubversionSampleRepoRule sampleRepo = new SubversionSampleRepoRule();

	@Test
	public void ofSource_Smoke() throws Exception {
		sampleRepo.init();
		sampleRepo.write("file", "trunk");
		sampleRepo.svnkit("commit", "--message=trunk", sampleRepo.wc());
		long trunk = sampleRepo.revision();
		assertEquals(3, trunk);
		sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
		sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
		sampleRepo.write("file", "dev1");
		sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
		long dev1 = sampleRepo.revision();
		assertEquals(5, dev1);
		sampleRepo.svnkit("copy", "--message=tagging", sampleRepo.branchesUrl() + "/dev",
				sampleRepo.tagsUrl() + "/dev-1");
		sampleRepo.write("file", "dev2");
		sampleRepo.svnkit("commit", "--message=dev2", sampleRepo.wc());
		long dev2 = sampleRepo.revision();
		assertEquals(7, dev2);
		SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
		try (SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("trunk"))) {
			assertThat(fs, notNullValue());
			SCMFile root = fs.getRoot();
			assertThat(root, notNullValue());
			assertTrue(root.isRoot());
			Iterable<SCMFile> children = root.children();
			Iterator<SCMFile> iterator = children.iterator();
			assertThat(iterator.hasNext(), is(true));
			SCMFile file = iterator.next();
			assertThat(iterator.hasNext(), is(false));
			assertThat(file.getName(), is("file"));
		}
	}

	@Test
	public void ofSourceRevision() throws Exception {
		sampleRepo.init();
		sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
		sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
		SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
		SCMRevision revision = source.fetch(new SCMHead("branches/dev"), null);
		sampleRepo.write("file", "modified");
		sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
		try (SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("branches/dev"), revision)) {
			assertThat(fs, notNullValue());
			SCMFile root = fs.getRoot();
			assertThat(root, notNullValue());
			Iterable<SCMFile> children = root.children();
			Iterator<SCMFile> iterator = children.iterator();
			assertThat(iterator.hasNext(), is(true));
			SCMFile file = iterator.next();
			assertThat(iterator.hasNext(), is(false));
			assertThat(file.getName(), is("file"));
			assertThat(file.contentAsString(), is(""));
		}
	}

	@Test
	public void lastModified_Smokes() throws Exception {
		sampleRepo.init();
		sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
		sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
		SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
		SCMRevision revision = source.fetch(new SCMHead("branches/dev"), null);
		sampleRepo.write("file", "modified");
		sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
		final long fileSystemAllowedOffset = isWindows() ? 4000 : 1500;
		try (SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("branches/dev"), revision);) {
			long currentTime = isWindows() ? System.currentTimeMillis() / 1000L * 1000L : System.currentTimeMillis();
			long lastModified = fs.lastModified();
			assertThat(lastModified, greaterThanOrEqualTo(currentTime - fileSystemAllowedOffset));
			assertThat(lastModified, lessThanOrEqualTo(currentTime + fileSystemAllowedOffset));
			SCMFile file = fs.getRoot().child("file");
			currentTime = isWindows() ? System.currentTimeMillis() / 1000L * 1000L : System.currentTimeMillis();
			lastModified = file.lastModified();
			assertThat(lastModified, greaterThanOrEqualTo(currentTime - fileSystemAllowedOffset));
			assertThat(lastModified, lessThanOrEqualTo(currentTime + fileSystemAllowedOffset));
		}
	}

	@Test
	public void directoryTraversal() throws Exception {
		sampleRepo.init();
		sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
		sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
		sampleRepo.mkdirs("dir/subdir");
		sampleRepo.svnkit("add", sampleRepo.wc() + "/dir");
		sampleRepo.svnkit("mv", sampleRepo.wc() + "/file", sampleRepo.wc() + "/dir/subdir/file");
		sampleRepo.write("dir/subdir/file", "modified");
		sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
		SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
		try (SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("branches/dev"))) {
			assertThat(fs, notNullValue());
			assertThat(fs.getRoot(), notNullValue());
			Iterable<SCMFile> children = fs.getRoot().children();
			Iterator<SCMFile> iterator = children.iterator();
			assertThat(iterator.hasNext(), is(true));
			SCMFile dir = iterator.next();
			assertThat(iterator.hasNext(), is(false));
			assertThat(dir.getName(), is("dir"));
			assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
			children = dir.children();
			iterator = children.iterator();
			assertThat(iterator.hasNext(), is(true));
			SCMFile subdir = iterator.next();
			assertThat(iterator.hasNext(), is(false));
			assertThat(subdir.getName(), is("subdir"));
			assertThat(subdir.getType(), is(SCMFile.Type.DIRECTORY));
			children = subdir.children();
			iterator = children.iterator();
			assertThat(iterator.hasNext(), is(true));
			SCMFile file = iterator.next();
			assertThat(iterator.hasNext(), is(false));
			assertThat(file.getName(), is("file"));
			assertThat(file.contentAsString(), is("modified"));
		}
	}

	@Test
	public void mixedContent() throws Exception {
		sampleRepo.init();
		sampleRepo.svnkit("copy", "--message=branching", sampleRepo.trunkUrl(), sampleRepo.branchesUrl() + "/dev");
		sampleRepo.svnkit("switch", sampleRepo.branchesUrl() + "/dev", sampleRepo.wc());
		sampleRepo.write("file", "modified");
		sampleRepo.write("file2", "new");
		sampleRepo.svnkit("add", sampleRepo.wc() + "/file2");
		sampleRepo.write("dir/file3", "modified");
		sampleRepo.svnkit("add", sampleRepo.wc() + "/dir");
		sampleRepo.svnkit("commit", "--message=dev1", sampleRepo.wc());
		SCMSource source = new SubversionSCMSource(null, sampleRepo.prjUrl());
		try (SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("branches/dev"));) {
			assertThat(fs, notNullValue());
			assertThat(fs.getRoot(), notNullValue());
			Iterable<SCMFile> children = fs.getRoot().children();
			Set<String> names = new TreeSet<String>();
			SCMFile file = null;
			SCMFile file2 = null;
			SCMFile dir = null;
			for (SCMFile f : children) {
				names.add(f.getName());
				if ("file".equals(f.getName())) {
					file = f;
				} else if ("file2".equals(f.getName())) {
					file2 = f;
				} else if ("dir".equals(f.getName())) {
					dir = f;
				}
			}
			assertThat(names, containsInAnyOrder(is("file"), is("file2"), is("dir")));
			assertThat(file.getType(), is(SCMFile.Type.REGULAR_FILE));
			assertThat(file2.getType(), is(SCMFile.Type.REGULAR_FILE));
			assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
			assertThat(file.contentAsString(), is("modified"));
			assertThat(file2.contentAsString(), is("new"));
		}
	}

	private boolean isWindows() {
		return java.io.File.pathSeparatorChar == ';';
	}
}
