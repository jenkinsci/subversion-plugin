package jenkins.scm.impl.subversion;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.Extension;
import hudson.model.Item;
import hudson.scm.CredentialsSVNAuthenticationProviderImpl;
import hudson.scm.FilterSVNAuthenticationManager;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SVNAuthStoreHandlerImpl;
import hudson.scm.SVNAuthenticationManager;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.security.ACL;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.subversion.SubversionSCMSource.SCMRevisionImpl;

public class SubversionSCMFileSystem extends SCMFileSystem {
	public static final String DISABLE_PROPERTY = SubversionSCMFileSystem.class.getName() + ".disable";
	private SVNRepository repo;

	protected SubversionSCMFileSystem(SVNRepository repo, SCMRevision rev) {
		super(rev);
		this.repo = repo;
	}

	@Override
	public long lastModified() throws IOException, InterruptedException {
		return getRoot().lastModified();
	}

	@Override
	public SCMFile getRoot() {
		return new SubversionSCMFile(this);
	}

	SVNRepository getRepository() {
		return repo;
	}
	
	@Override
	public SubversionSCMSource.SCMRevisionImpl getRevision() {
		return (SCMRevisionImpl) super.getRevision();
	}
	
	long getLatestRevision() throws SVNException {
		if (isFixedRevision()) {
			return getRevision().getRevision();
		} else {
			return repo.getLatestRevision();
		}
	}
	
	@Extension
	public static class BuilderImpl extends SCMFileSystem.Builder {
		public final boolean ENABLED = !"true".equalsIgnoreCase(System.getProperty(DISABLE_PROPERTY));

		@Override
		public boolean supports(SCM source) {
			return source instanceof SubversionSCM && ENABLED;
		}

        @Override
        protected boolean supportsDescriptor(SCMDescriptor descriptor) {
            return descriptor instanceof SubversionSCM.DescriptorImpl && ENABLED;
        }

		@Override
		public boolean supports(SCMSource source) {
			return source instanceof SubversionSCMSource && ENABLED;
		}

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor descriptor) {
            return descriptor instanceof SubversionSCMSource.DescriptorImpl && ENABLED;
        }

		@Override
		public SCMFileSystem build(SCMSource source, SCMHead head, SCMRevision rev)
				throws IOException, InterruptedException {
            return build(source.getOwner(), source.build(head, rev), rev);
		}
		
		@Override
		public SCMFileSystem build(Item owner, SCM scm, SCMRevision rev) throws IOException, InterruptedException {
			if (rev != null && !(rev instanceof SubversionSCMSource.SCMRevisionImpl)) {
				return null;
			}
			try {
				SubversionSCM svn = (SubversionSCM) scm;
				ModuleLocation moduleLocation = svn.getLocations()[0];
				StandardCredentials credentials = null;
				SVNURL repoURL = moduleLocation.getSVNURL();
				if (moduleLocation.credentialsId != null) {
					credentials = CredentialsMatchers.firstOrNull(
							CredentialsProvider.lookupCredentials(StandardCredentials.class, owner, ACL.SYSTEM,
									URIRequirementBuilder.fromUri(repoURL.toString()).build()),
							CredentialsMatchers.allOf(CredentialsMatchers.withId(moduleLocation.credentialsId),
									CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardCredentials.class),
											CredentialsMatchers.instanceOf(SSHUserPrivateKey.class))));
				}
				SVNRepository repository = createRepository(repoURL, credentials);
				return new SubversionSCMFileSystem(repository, rev);
			} catch (SVNException e) {
				throw new IOException("failed to create SVNRepositoryView", e);
			}
		}

		// Copied from SVNRepositoryView
		private SVNRepository createRepository(SVNURL repoURL, StandardCredentials credentials) throws SVNException {
			SVNRepository repository = null;
			repository = SVNRepositoryFactory.create(repoURL);
			File configDir = SVNWCUtil.getDefaultConfigurationDirectory();

			ISVNAuthenticationManager sam = new SVNAuthenticationManager(configDir, null, null);

			sam.setAuthenticationProvider(new CredentialsSVNAuthenticationProviderImpl(credentials));
			SVNAuthStoreHandlerImpl.install(sam);
			sam = new FilterSVNAuthenticationManager(sam) {
				// If there's no time out, the blocking read operation may hang forever, because TCP itself
				// has no timeout. So always use some time out. If the underlying implementation gives us some
				// value (which may come from ~/.subversion), honor that, as long as it sets some timeout value.
				@Override
				public int getReadTimeout(SVNRepository repository) {
					int r = super.getReadTimeout(repository);
					if (r <= 0) {
						r = (int) TimeUnit.MINUTES.toMillis(1);
					}
					return r;
				}
			};
			repository.setTunnelProvider(SVNWCUtil.createDefaultOptions(true));
			repository.setAuthenticationManager(sam);
			return repository;
		}
	}

	@Override
	public void close() {
		repo.closeSession();
	}
}
