package jenkins.scm.impl.subversion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;

import jenkins.scm.api.SCMFile;

public class SubversionSCMFile extends SCMFile {

	private SubversionSCMFileSystem fs;

	SubversionSCMFile(SubversionSCMFileSystem fs) {
		this.fs = fs;
	}

	SubversionSCMFile(SubversionSCMFile parent, String name, SubversionSCMFileSystem fs) {
		super(parent, name);
		this.fs = fs;
	}

	@Override
	protected SCMFile newChild(String name, boolean assumeIsDirectory) {
		return new SubversionSCMFile(this, name, fs);
	}

	@Override
	public Iterable<SCMFile> children() throws IOException, InterruptedException {
		try {
			List<SCMFile> result = new ArrayList<>();
			Collection<SVNDirEntry> dirEntries = new ArrayList<>();
			fs.getRepository().getDir(getPath(), fs.getLatestRevision(), null, 0, dirEntries);
			for (SVNDirEntry e : dirEntries) {
				result.add(newChild(e.getName(), false));
			}
			return result;
		} catch (SVNException e) {
			throw new IOException("failed to list children for " + getPath(), e);
		}
	}
	
	@Override
	public long lastModified() throws IOException, InterruptedException {
		return getInfo().getDate().getTime();
	}

	@Override
	protected Type type() throws IOException, InterruptedException {
		if (isRoot()) {
			return Type.DIRECTORY;
		} else {
			try {
				SVNNodeKind nodeKind = fs.getRepository().checkPath(getPath(), fs.getLatestRevision());
				switch (nodeKind.toString()) {
				case "file":
					return Type.REGULAR_FILE;
				case "dir":
					return Type.DIRECTORY;
				case "none":
					return Type.NONEXISTENT;
				default:
					return Type.OTHER;
				}
			} catch (SVNException e) {
				throw new IOException("failed to get file type for " + getPath(), e);
			}
		}
	}

	@Override
	public InputStream content() throws IOException, InterruptedException {
		ByteArrayOutputStream contents = new ByteArrayOutputStream();
		try {
			fs.getRepository().getFile(getPath(), fs.getLatestRevision(), null, contents);
			return new ByteArrayInputStream(contents.toByteArray());
		} catch (SVNException e) {
			throw new IOException("failed to fetch file: " + getPath(), e);
		}
	}

	private SVNDirEntry getInfo() throws IOException {
		try {
			return fs.getRepository().info(getPath(), fs.getLatestRevision());
		} catch (SVNException e) {
			throw new IOException("failed to get file info for " + getPath(), e);
		}
	}
}
