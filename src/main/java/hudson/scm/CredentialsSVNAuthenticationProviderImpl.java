package hudson.scm;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.UsernameCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.remoting.Channel;
import hudson.util.Scrambler;
import org.apache.commons.beanutils.PropertyUtils;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;

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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * @author stephenc
 * @since 08/08/2013 12:15
 */
public class CredentialsSVNAuthenticationProviderImpl implements ISVNAuthenticationProvider, Serializable {

    private static final long serialVersionUID = 1L;

    private final SVNAuthenticationBuilderProvider provider;

    public CredentialsSVNAuthenticationProviderImpl(Credentials credentials) {
        this.provider =
                new RemotableSVNAuthenticationBuilderProvider(credentials, Collections.<String, Credentials>emptyMap());
    }

    public CredentialsSVNAuthenticationProviderImpl(Credentials credentials,
                                                    Map<String, Credentials> credentialsByRealm) {
        this.provider = new RemotableSVNAuthenticationBuilderProvider(credentials, credentialsByRealm);
    }

    public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm,
                                                         SVNErrorMessage errorMessage, SVNAuthentication previousAuth,
                                                         boolean authMayBeStored) {
        SVNAuthenticationBuilder builder = provider.getBuilder(realm);
        if (builder == null) {
            // finished all auth strategies, we are out of luck
            return null;
        }
        List<SVNAuthentication> authentications = builder.build(kind, url);
        int index = previousAuth == null ? 0 : indexOf(authentications, previousAuth) + 1;
        if (index >= authentications.size()) {
            return null;
        }
        return authentications.get(index);
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

    public static interface SVNAuthenticationBuilderProvider extends Serializable {
        SVNAuthenticationBuilder getBuilder(String realm);
    }

    public static class RemotableSVNAuthenticationBuilderProvider implements SVNAuthenticationBuilderProvider {

        private static final long serialVersionUID = 1L;

        private final Credentials defaultCredentials;
        private final Map<String, Credentials> credentialsByRealm;

        public RemotableSVNAuthenticationBuilderProvider(Credentials defaultCredentials,
                                                         Map<String, Credentials> credentialsByRealm) {
            this.defaultCredentials = defaultCredentials;
            this.credentialsByRealm = credentialsByRealm;
        }

        /**
         * When sent to the remote node, send a proxy.
         */
        private Object writeReplace() {
            return Channel.current().export(SVNAuthenticationBuilderProvider.class, this);
        }

        public SVNAuthenticationBuilder getBuilder(String realm) {
            Credentials c = credentialsByRealm.get(realm);
            if (c == null) {
                c = defaultCredentials;
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
            passphrase = Scrambler.scramble(c.getPassphrase().getPlainText());
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
            KeyStore.PasswordProtection passwordProtection =
                    new KeyStore.PasswordProtection(password.toCharArray());
            try {
                // ensure we map the keystore to the correct type
                KeyStore dst = KeyStore.getInstance("PKCS12");
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
                                            .create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate")
                                            .initCause(e2));
                        }
                    }
                    dst.setEntry(alias, entry, passwordProtection);
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                dst.store(bos, password.toCharArray());
                certificateFile = bos.toByteArray();
            } catch (KeyStoreException e) {
                throw new RuntimeException(
                        SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate")
                                .initCause(e));
            } catch (CertificateException e) {
                throw new RuntimeException(
                        SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate")
                                .initCause(e));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(
                        SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate")
                                .initCause(e));
            } catch (IOException e) {
                throw new RuntimeException(
                        SVNErrorMessage.create(SVNErrorCode.AUTHN_CREDS_UNAVAILABLE, "Unable to save certificate")
                                .initCause(e));
            }
        }

        public List<SVNAuthentication> build(String kind, SVNURL url) {
            if (ISVNAuthenticationManager.SSL.equals(kind)) {
                return Collections.<SVNAuthentication>singletonList(
                        new SVNSSLAuthentication(certificateFile, Scrambler.descramble(password), false, url, false));
            }
            return Collections.emptyList();
        }
    }

}
