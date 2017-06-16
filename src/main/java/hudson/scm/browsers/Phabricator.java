package hudson.scm.browsers;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.scm.SubversionChangeLogSet;
import hudson.scm.SubversionChangeLogSet.Path;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Svn Browser for Phabricator
 */
public class Phabricator extends SubversionRepositoryBrowser {

    private static final long serialVersionUID = 1L;

    /**
     * The repo id for the project 
     *
     * <p>Without leading "r"</p>
     */
    public final String repo;

    /**
     * The URL of the top of the site.
     *
     * <p>Normalized to ends with '/', like <tt>http://svn.apache.org/wsvn/</tt>
     * It may contain a query parameter like <tt>?root=foobar</tt>, so relative
     * URL construction needs to be done with care.</p>
     */
    public final URL url;
	
	
    @DataBoundConstructor
    public Phabricator(URL url, String repo) {
        this.url = normalizeToEndWithSlash(url);
        this.repo = repo;
    }

	public URL getUrl()
	{
		return url;
	}
	
    public String getRepo() {
        return repo;
    }

    /**
     * Creates a link to the changeset
     *
     * https://[Phabricator URL]/r$repo$revision
     *
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getChangeSetLink(SubversionChangeLogSet.LogEntry changeSet) throws IOException {
        return new URL(getUrl(), String.format("/r%s%s", this.getRepo(), changeSet.getRevision()));
    }

    /**
     * Creates a link to the commit diff.
     *
     * https://[Phabricator URL]/diffusion/$repo/change/master/$path;$revision
     *
     *
     * @param path
     * @return diff link
     * @throws IOException
     */
    @Override
    public URL getDiffLink(Path path) throws IOException {
		if (path.getEditType() != EditType.EDIT) {
            return null; // no diff if this is not an edit change
        }

        int r = path.getLogEntry().getRevision();
        final String spec = String.format("/diffusion/%s/change/master/%s;%i", this.getRepo(), path.getPath(), r);
        return new URL(getUrl(), spec);
    }

    /**
     * Creates a link to the file.
     * https://[Phabricator URL]/diffusion/$repo/history/master/$path;$revision
     *
     * @param path
     * @return file link
     * @throws IOException
     */
    @Override
    public URL getFileLink(Path path) throws IOException {
        int r = path.getLogEntry().getRevision();
        final String spec = String.format("/diffusion/%s/history/master/%s;%i", this.getRepo(), path.getPath(), r);
        return new URL(getUrl(), spec);
    }

    @Extension
    public static class PhabricatorDescriptor extends Descriptor<RepositoryBrowser<?>> {
        public String getDisplayName() {
            return "Phabricator";
        }
    }
}
