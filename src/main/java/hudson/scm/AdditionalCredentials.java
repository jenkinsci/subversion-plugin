package hudson.scm;

import static hudson.Util.fixEmptyAndTrim;
import static hudson.scm.PollingResult.BUILD_NOW;
import static hudson.scm.PollingResult.NO_CHANGES;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.DomainSpecification;
import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import com.cloudbees.plugins.credentials.domains.HostnameSpecification;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeSpecification;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.BulkChange;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.model.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.UnsupportedCharsetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;

import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.scm.UserProvidedCredential.AuthenticationManagerImpl;
import hudson.scm.subversion.CheckoutUpdater;
import hudson.scm.subversion.Messages;
import hudson.scm.subversion.SvnHelper;
import hudson.scm.subversion.UpdateUpdater;
import hudson.scm.subversion.UpdateWithRevertUpdater;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.scm.subversion.WorkspaceUpdater.UpdateTask;
import hudson.scm.subversion.WorkspaceUpdaterDescriptor;
import hudson.util.FormValidation;
import hudson.util.LogTaskListener;
import hudson.util.MultipartFormDataParser;
import hudson.util.Scrambler;
import hudson.util.Secret;
import hudson.util.TimeUnit2;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamResult;

import net.sf.json.JSONObject;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.dav.http.DefaultHTTPConnectionFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.io.SVNCapability;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.crypto.Base64;
import javax.annotation.Nonnull;
import jenkins.MasterToSlaveFileCallable;
import jenkins.security.Roles;
import jenkins.security.SlaveToMasterCallable;
import org.jenkinsci.remoting.RoleChecker;


public class AdditionalCredentials extends AbstractDescribableImpl<AdditionalCredentials> {
    @NonNull
    private final String realm;
    @CheckForNull
    private final String credentialsId;

    @DataBoundConstructor
    public AdditionalCredentials(@NonNull String realm, @CheckForNull String credentialsId) {
        realm.getClass(); // throw NPE if null
        this.realm = realm;
        this.credentialsId = credentialsId;
    }

    @NonNull
    public String getRealm() {
        return realm;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdditionalCredentials)) {
            return false;
        }

        AdditionalCredentials that = (AdditionalCredentials) o;

        if (!realm.equals(that.realm)) {
            return false;
        }
        if (credentialsId != null ? !credentialsId.equals(that.credentialsId) : that.credentialsId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = realm.hashCode();
        result = 31 * result + (credentialsId != null ? credentialsId.hashCode() : 0);
        return result;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AdditionalCredentials> {

        @Override
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String realm) {
            if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel();
            }
            List<DomainRequirement> domainRequirements;
            if (realm == null) {
                domainRequirements = Collections.<DomainRequirement>emptyList();
            } else {
                if (realm.startsWith("<") && realm.contains(">")) {
                    int index = realm.indexOf('>');
                    assert index > 1;
                    domainRequirements = URIRequirementBuilder.fromUri(realm.substring(1, index).trim()).build();
                } else {
                    domainRequirements = Collections.<DomainRequirement>emptyList();
                }
            }
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                    CredentialsMatchers.instanceOf(SSHUserPrivateKey.class)
                            ),
                            CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                    context,
                                    ACL.SYSTEM,
                                    domainRequirements)
                    );
        }

    }
}