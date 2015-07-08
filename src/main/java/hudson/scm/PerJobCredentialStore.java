package hudson.scm;

import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Saveable;
import hudson.remoting.Channel;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.SubversionSCM.DescriptorImpl.Credential;
import hudson.scm.SubversionSCM.DescriptorImpl.RemotableSVNAuthenticationProvider;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCredentials;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Persists the credential per job. This object is remotable.
 *
 * @author Kohsuke Kawaguchi
 */
final class PerJobCredentialStore implements Saveable, RemotableSVNAuthenticationProvider {
    private static final long serialVersionUID = 8509067873170016632L;

    private final transient AbstractProject<?,?> project;

    /**
     * SVN authentication realm to its associated credentials, scoped to this project.
     */
    private final Map<String,Credential> credentials = new Hashtable<String,Credential>();
    
    PerJobCredentialStore(AbstractProject<?,?> project) {
        this.project = project;
        // read existing credential
        XmlFile xml = getXmlFile();
        try {
            if (xml.exists())
                xml.unmarshal(this);
        } catch (IOException e) {
            // ignore the failure to unmarshal, or else we'll never get through beyond this point.
            LOGGER.log(INFO,"Failed to retrieve Subversion credentials from "+xml,e);
        }
    }

    public synchronized Credential get(String realm) {
        return credentials.get(realm);
    }

    public Credential getCredential(SVNURL url, String realm) {
        return get(realm);
    }

    public void acknowledgeAuthentication(String realm, Credential cred) {
        try {
            acknowledge(realm, cred);
        } catch (IOException e) {
            LOGGER.log(INFO,"Failed to persist the credentials",e);
        }
    }

    public synchronized void acknowledge(String realm, Credential cred) throws IOException {
        Credential old = cred==null ? credentials.remove(realm) : credentials.put(realm, cred);
        // save only if there was a change
        if (old==null && cred==null)    return;
        if (old==null || cred==null || !old.equals(cred))
            save();
    }

    public synchronized void save() throws IOException {
        IS_SAVING.set(Boolean.TRUE);
        try {
            getXmlFile().write(this);
        } finally {
            IS_SAVING.remove();
        }
    }

    private XmlFile getXmlFile() {
        return new XmlFile(new File(project.getRootDir(),"subversion.credentials"));
    }

    /*package*/ synchronized boolean isEmpty() {
        return credentials.isEmpty();
    }

    /**
     * When sent to the remote node, send a proxy.
     */
    private Object writeReplace() {
        if (IS_SAVING.get()!=null)  return this;
        
        Channel c = Channel.current();
        return c==null ? this : c.export(RemotableSVNAuthenticationProvider.class, this);
    }

    private static final Logger LOGGER = Logger.getLogger(PerJobCredentialStore.class.getName());

    /**
     * Used to remember the context. If we are persisting, we don't want to persist a proxy,
     * even if that happens in the context of a remote call.
     */
    private static final ThreadLocal<Boolean> IS_SAVING = new ThreadLocal<Boolean>();

    /*package*/ void migrateCredentials(SubversionSCM.DescriptorImpl descriptor) throws IOException {
        Iterable<CredentialsStore> it = CredentialsProvider.lookupStores(project);
        if (it != null && it.iterator().hasNext()) {
            CredentialsStore store = it.iterator().next();
            for (Map.Entry<String, Credential> e : credentials.entrySet()) {
                StandardCredentials credential =  descriptor.migrateCredentials(store, e.getKey(), e.getValue());
                ModuleLocation[] locations = ((SubversionSCM) project.getScm()).getLocations();
                for (int i = 0; i < locations.length; i++) {
                    try {
                        if (e.getKey().contains(locations[i].getSVNURL().getHost())) {
                            locations[i].setCredentialsId(credential.getId());
                            break;
                        }
                    } catch (SVNException ex) {
                        // Should not happen, but...
                        LOGGER.log(WARNING, "Repository location with a malformed URL: " + locations[i].remote, ex);
                    }
                }
            }
        }
    }
}
