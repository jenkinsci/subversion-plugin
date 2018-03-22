package hudson.scm;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.scm.subversion.Messages;
import hudson.security.ACL;
import hudson.util.Scrambler;
import hudson.util.Secret;
import jenkins.scm.impl.subversion.RemotableSVNErrorMessage;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.*;

import javax.security.auth.DestroyFailedException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Set;

/**
 * @author stephenc
 * @since 08/08/2013 12:15
 */
public class CredentialsSVNAuthenticationProviderImpl implements ISVNAuthenticationProvider, Serializable {

    private static final long serialVersionUID = 1L;

    private final SVNAuthenticationBuilderProvider provider;

    private final SVNUnauthenticatedRealmObserver realmObserver = new RemotableSVNUnauthenticatedRealmObserver();

    private static final SVNAuthentication ANONYMOUS = new SVNUserNameAuthentication("", false, null, false);

    public CredentialsSVNAuthenticationProviderImpl(Credentials credentials) {
        this(credentials, null, /* TODO */ TaskListener.NULL);
    }

    public CredentialsSVNAuthenticationProviderImpl(Credentials credentials,
                                                    Map<String, Credentials> credentialsByRealm,
                                                    TaskListener listener) {
        this.provider = new RemotableSVNAuthenticationBuilderProvider(credentials,
                credentialsByRealm == null ? Collections.<String, Credentials>emptyMap() : credentialsByRealm, listener);
    }

    @Deprecated
    public CredentialsSVNAuthenticationProviderImpl(Credentials credentials,
                                                    Map<String, Credentials> credentialsByRealm) {
        this(credentials, credentialsByRealm, TaskListener.NULL);
    }

    public static CredentialsSVNAuthenticationProviderImpl createAuthenticationProvider(Item context, String remote, String credentialsId, Map<String,String> additionalCredentialIds, TaskListener listener) {
        StandardCredentials defaultCredentials;
        if (credentialsId == null) {
            defaultCredentials = null;
        } else {
            defaultCredentials = CredentialsMatchers
                    .firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                            ACL.SYSTEM, URIRequirementBuilder.fromUri(remote).build()),
                            CredentialsMatchers.allOf(idMatcher(credentialsId),
                                    CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(
                                            StandardCredentials.class), CredentialsMatchers.instanceOf(
                                            SSHUserPrivateKey.class))));
        }
        Map<String, Credentials> additional = new HashMap<String, Credentials>();
        if (additionalCredentialIds != null) {
            for (Map.Entry<String,String> c : additionalCredentialIds.entrySet()) {
                if (c.getValue() != null) {
                    StandardCredentials cred = CredentialsMatchers
                            .firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                                    ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                                    CredentialsMatchers.allOf(idMatcher(c.getValue()),
                                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(
                                                    StandardCredentials.class), CredentialsMatchers.instanceOf(
                                                    SSHUserPrivateKey.class))));
                    if (cred != null) {
                        additional.put(c.getKey(), cred);
                    }
                }
            }
        }
        return new CredentialsSVNAuthenticationProviderImpl(defaultCredentials, additional, listener);
    }

    @Deprecated
    public static CredentialsSVNAuthenticationProviderImpl createAuthenticationProvider(Item context, String remote, String credentialsId, Map<String,String> additionalCredentialIds) {
        return createAuthenticationProvider(context, remote, credentialsId, additionalCredentialIds, TaskListener.NULL);
    }

    public static CredentialsSVNAuthenticationProviderImpl createAuthenticationProvider(Item context, SubversionSCM scm,
                                                                                        SubversionSCM.ModuleLocation
                                                                                                location,
                                                                                        TaskListener listener) {
        StandardCredentials defaultCredentials;
        if (location == null) {
            defaultCredentials = null;
        } else {
            defaultCredentials = CredentialsMatchers
                    .firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                            ACL.SYSTEM, URIRequirementBuilder.fromUri(location.remote).build()),
                            CredentialsMatchers.allOf(idMatcher(location.credentialsId),MATCHER));
        }
        Map<String, Credentials> additional = new HashMap<String, Credentials>();
        if (scm != null) {
            for (SubversionSCM.AdditionalCredentials c : scm.getAdditionalCredentials()) {
                if (c.getCredentialsId() != null) {
                    StandardCredentials cred = CredentialsMatchers
                            .firstOrNull(CredentialsProvider.lookupCredentials(StandardCredentials.class, context,
                                    ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                                    CredentialsMatchers.allOf(idMatcher(c.getCredentialsId()),MATCHER));
                    if (cred != null) {
                        additional.put(c.getRealm(), cred);
                    }
                }
            }
        }
        return new CredentialsSVNAuthenticationProviderImpl(defaultCredentials, additional, listener);
    }

    @Deprecated
    public static CredentialsSVNAuthenticationProviderImpl createAuthenticationProvider(Item context, SubversionSCM scm,
                                                                                        SubversionSCM.ModuleLocation
                                                                                                location) {
        return createAuthenticationProvider(context, scm, location, TaskListener.NULL);
    }

    private static CredentialsMatcher idMatcher(String credentialsId) {
        return credentialsId == null ? CredentialsMatchers.never() : CredentialsMatchers.withId(credentialsId);
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm,
                                                         SVNErrorMessage errorMessage, SVNAuthentication previousAuth,
                                                         boolean authMayBeStored) {
        LOGGER.fine("Attempting auth for URL: " + url.toString() + "; Realm: " + realm);
        SVNAuthenticationBuilder builder = provider.getBuilder(realm);
        if (builder == null) {
            if (previousAuth == null && ISVNAuthenticationManager.USERNAME.equals(kind)) {
                return ANONYMOUS;
            }
            realmObserver.observe(realm);
            // finished all auth strategies, we are out of luck
            return null;
        }
        List<SVNAuthentication> authentications = builder.build(kind, url);
        int index = previousAuth == null ? 0 : indexOf(authentications, previousAuth) + 1;
        if (index >= authentications.size()) {
            if (previousAuth == null && ISVNAuthenticationManager.USERNAME.equals(kind)) {
                return ANONYMOUS;
            }
            realmObserver.observe(realm);
            return null;
        }
        return authentications.get(index);
    }

    public void resetUnauthenticatedRealms() {
        realmObserver.reset();
    }

    public Set<String> getUnauthenticatedRealms() {
        return realmObserver.get();
    }

    private static int indexOf(List<SVNAuthentication> list, SVNAuthentication o) {
        int index = 0;
        for (SVNAuthentication v : list) {
            if (equals(v, o)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean equals(SVNAuthentication a1, SVNAuthentication a2) {
        if (a1 == null && a2 == null) {
            return true;
        }
        if (a1 == null || a2 == null) {
            return false;
        }
        if (a1.getClass() != a2.getClass()) {
            return false;
        }

        try {
            return describeBean(a1).equals(describeBean(a2));
        } catch (IllegalAccessException e) {
            return false;
        } catch (InvocationTargetException e) {
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * In preparation for a comparison, char[] needs to be converted that supports value equality.
     */
    @SuppressWarnings("unchecked")
    private static Map describeBean(Object o)
            throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Map<?, ?> m = PropertyUtils.describe(o);
        for (Map.Entry e : m.entrySet()) {
            Object v = e.getValue();
            if (v instanceof char[]) {
                char[] chars = (char[]) v;
                e.setValue(new String(chars));
            }
        }
        return m;
    }


    public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
        return ACCEPTED_TEMPORARY;
    }

    public static interface SVNUnauthenticatedRealmObserver extends Serializable {
        void observe(String realm);

        void reset();

        Set<String> get();
    }

    public static class RemotableSVNUnauthenticatedRealmObserver implements SVNUnauthenticatedRealmObserver {

        private final Set<String> realms = new LinkedHashSet<String>();

        /**
         * When sent to the remote node, send a proxy.
         */
        private Object writeReplace() {
            return Channel.current().export(SVNUnauthenticatedRealmObserver.class, this);
        }

        public void observe(String realm) {
            synchronized (realms) {
                realms.add(realm);
            }
        }

        public void reset() {
            synchronized (realms) {
                realms.clear();
            }
        }

        public Set<String> get() {
            synchronized (realms) {
                return new LinkedHashSet<String>(realms);
            }
        }
    }

    public static interface SVNAuthenticationBuilderProvider extends Serializable {
        SVNAuthenticationBuilder getBuilder(String realm);
    }

    public static class RemotableSVNAuthenticationBuilderProvider implements SVNAuthenticationBuilderProvider {

        private static final long serialVersionUID = 1L;

        private final Credentials defaultCredentials;
        private final Map<String, Credentials> credentialsByRealm;
        @CheckForNull
        private final TaskListener listener;

        public RemotableSVNAuthenticationBuilderProvider(Credentials defaultCredentials,
                                                         Map<String, Credentials> credentialsByRealm,
                                                         TaskListener listener) {
            this.defaultCredentials = defaultCredentials;
            this.credentialsByRealm = credentialsByRealm;
            this.listener = listener == TaskListener.NULL ? null : listener;
        }

        @Deprecated
        public RemotableSVNAuthenticationBuilderProvider(Credentials defaultCredentials,
                                                         Map<String, Credentials> credentialsByRealm) {
            this(defaultCredentials, credentialsByRealm, TaskListener.NULL);
        }

        /**
         * When sent to the remote node, send a proxy.
         */
        private Object writeReplace() {
            return Channel.current().export(SVNAuthenticationBuilderProvider.class, this);
        }

        @Override
        public SVNAuthenticationBuilder getBuilder(String realm) {
            TaskListener l = listener == null ? TaskListener.NULL : listener;
            Credentials c = credentialsByRealm.get(realm);
            if (c != null) {
                l.getLogger().println(Messages.CredentialsSVNAuthenticationProviderImpl_credentials_in_realm(CredentialsNameProvider.name(c), realm));
            } else {
                c = defaultCredentials;
                String name = c != null ? CredentialsNameProvider.name(c) : "<none>";
                if (credentialsByRealm.isEmpty()) {
                    l.getLogger().println(Messages.CredentialsSVNAuthenticationProviderImpl_sole_credentials(name, realm));
                } else {
                    l.getLogger().println(Messages.CredentialsSVNAuthenticationProviderImpl_missing_credentials(realm, StringUtils.join(credentialsByRealm.keySet(), "’, ‘"), name));
                }
            }
            if (c instanceof CertificateCredentials) {
                return new SVNCertificateAuthenticationBuilder((CertificateCredentials) c);
            }
            if (c instanceof SSHUserPrivateKey) {
                return new SVNUsernamePrivateKeysAuthenticationBuilder(
                        (SSHUserPrivateKey) c);
            }
            if (c instanceof UsernamePasswordCredentials) {
                return new SVNUsernamePasswordAuthenticationBuilder((UsernamePasswordCredentials) c);
            }
            if (c instanceof UsernameCredentials) {
                return new SVNUsernameAuthenticationBuilder((UsernameCredentials) c);
            }
            return new SVNEmptyAuthenticationBuilder();
        }
    }

    public static interface SVNAuthenticationBuilder extends Serializable {
        List<SVNAuthentication> build(String kind, SVNURL url);
    }

    public static class SVNEmptyAuthenticationBuilder implements SVNAuthenticationBuilder {

        private static final long serialVersionUID = 1L;

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            return Collections.emptyList();
        }
    }

    public static class SVNUsernameAuthenticationBuilder implements SVNAuthenticationBuilder {

        private static final long serialVersionUID = 1L;

        private final String username;

        public SVNUsernameAuthenticationBuilder(UsernameCredentials c) {
            this.username = c.getUsername();
        }

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            if (ISVNAuthenticationManager.USERNAME.equals(kind)) {
                return Collections.<SVNAuthentication>singletonList(
                        new SVNUserNameAuthentication(username, true, url, true));
            }
            return Collections.emptyList();
        }
    }

    public static class SVNUsernamePasswordAuthenticationBuilder implements SVNAuthenticationBuilder {

        private static final long serialVersionUID = 1L;

        private final String username;
        private final String password;

        public SVNUsernamePasswordAuthenticationBuilder(UsernamePasswordCredentials c) {
            this.username = c.getUsername();
            this.password = Scrambler.scramble(c.getPassword().getPlainText());
        }

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                return Collections.<SVNAuthentication>singletonList(
                        new SVNPasswordAuthentication(username, Scrambler.descramble(password), false, url, false));
            }
            if (ISVNAuthenticationManager.SSH.equals(kind)) {
                return Collections.<SVNAuthentication>singletonList(
                        new SVNSSHAuthentication(username, Scrambler.descramble(password), -1, false, url, false));
            }
            return Collections.emptyList();
        }
    }

    public static class SVNUsernamePrivateKeysAuthenticationBuilder implements SVNAuthenticationBuilder {
        private static final long serialVersionUID = 1L;

        private final String username;
        private final String passphrase;
        private final List<String> privateKeys;

        public SVNUsernamePrivateKeysAuthenticationBuilder(SSHUserPrivateKey c) {
            username = c.getUsername();
            Secret secret = c.getPassphrase();
            this.passphrase = secret != null ? Scrambler.scramble(secret.getPlainText()) : null;
            privateKeys = new ArrayList<String>(c.getPrivateKeys());
        }

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            List<SVNAuthentication> result = new ArrayList<SVNAuthentication>();
            if (ISVNAuthenticationManager.SSH.equals(kind)) {
                for (String privateKey : privateKeys) {
                    result.add(new SVNSSHAuthentication(username, privateKey.toCharArray(),
                            Scrambler.descramble(passphrase), -1, false, url, false));
                }
            }
            return result;
        }
    }

    public static class SVNCertificateAuthenticationBuilder implements SVNAuthenticationBuilder {
        private static final long serialVersionUID = 1L;

        private final byte[] certificateFile;
        private final String password;

        public SVNCertificateAuthenticationBuilder(CertificateCredentials c) {
            String password = c.getPassword().getPlainText();
            this.password = Scrambler.scramble(password);
            char[] passwordChars = password.toCharArray();
            KeyStore.PasswordProtection passwordProtection =
                    new KeyStore.PasswordProtection(passwordChars);
            try {
                // ensure we map the keystore to the correct type
                KeyStore dst = KeyStore.getInstance("PKCS12");
                dst.load(null, null);
                KeyStore src = c.getKeyStore();
                for (Enumeration<String> e = src.aliases(); e.hasMoreElements(); ) {
                    String alias = e.nextElement();
                    KeyStore.Entry entry;
                    try {
                        entry = src.getEntry(alias, null);
                    } catch (UnrecoverableEntryException e1) {
                        try {
                            entry = src.getEntry(alias, passwordProtection);
                        } catch (UnrecoverableEntryException e2) {
                            throw new RuntimeException(
                                    SVNErrorMessage
                                            .create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate").getFullMessage(),
                                            e2);
                        }
                    }
                    dst.setEntry(alias, entry, passwordProtection);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                dst.store(bos, passwordChars);
                certificateFile = bos.toByteArray();
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                throw new RuntimeException(
                        new RemotableSVNErrorMessage(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate").getFullMessage(),
                                e);
            } finally {
                try {
                    passwordProtection.destroy();
                } catch (DestroyFailedException e) {
                    // ignore
                }
                Arrays.fill(passwordChars, ' ');
            }
        }

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            if (ISVNAuthenticationManager.SSL.equals(kind)) {
                SVNSSLAuthentication authentication = SVNSSLAuthentication.newInstance(
                        certificateFile,
                        Scrambler.descramble(password).toCharArray(),
                        false, url, false);
                return Collections.<SVNAuthentication>singletonList(
                        authentication);
            }
            return Collections.emptyList();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CredentialsSVNAuthenticationProviderImpl.class.getName());

    /**
     * {@link CredentialsMatcher} that matches either {@link StandardCredentials} or {@link SSHUserPrivateKey}
     */
    private static final CredentialsMatcher MATCHER = CredentialsMatchers.anyOf(
            CredentialsMatchers.instanceOf(StandardCredentials.class),
            CredentialsMatchers.instanceOf(SSHUserPrivateKey.class));
}
