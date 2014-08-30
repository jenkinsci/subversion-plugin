/*
 * The MIT License
 * 
 * Copyright (c) 2014 schristou88
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.scm;

import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author schristou88
 */
public class SVNSSLAuthentication extends SVNAuthentication {

    public static final String MSCAPI = "MSCAPI";
    public static final String SSL = "SSL";

    private byte[] myCertificate;
    private String myPassword;
    private String mySSLKind;
    private String myAlias;
    private String myCertificatePath;

   /**
     * Creates an SSL credentials object.
     *
     * @param certFile         user's certificate file
     * @param password         user's password
     * @param storageAllowed   to store or not this credential in a
     *                         credentials cache
     */
    public SVNSSLAuthentication(File certFile, String password, boolean storageAllowed) throws IOException {
        super(ISVNAuthenticationManager.SSL, null, storageAllowed);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(certFile);
        try {
            SVNTranslator.copy(in, baos);
        } finally {
            in.close();
        }
        myCertificate = baos.toByteArray();
        myPassword = password;

    }

    public SVNSSLAuthentication(byte[] certFile, String password, boolean storageAllowed) {
        this(certFile,password,storageAllowed,null,false);
    }

    /**
     * Creates an SSL credentials object.
     *
     * @param certFile         user's certificate file
     * @param password         user's password
     * @param storageAllowed   to store or not this credential in a
     *                         credentials cache
     * @param url              url these credentials are applied to
     * @since 1.3.1
     */
    public SVNSSLAuthentication(byte[] certFile, String password, boolean storageAllowed, SVNURL url, boolean isPartial) {
        super(ISVNAuthenticationManager.SSL, null, storageAllowed, url, isPartial);
        myCertificate = certFile;
        myPassword = password;
        mySSLKind = SSL;
    }

    public SVNSSLAuthentication(String sslKind, String alias, boolean storageAllowed) throws IOException {
        this((File) null, null, storageAllowed);
        mySSLKind = sslKind;
        myAlias = alias;
    }

    /**
     * Return a user's password.
     *
     * @return a password
     */
    public String getPassword() {
        return myPassword;
    }

    /**
     * Returns a user's certificate file.
     *
     * @return certificate file
     */
    public byte[] getCertificateFile() {
        return myCertificate;
    }

    /**
     * Returns the SSL kind.
     *
     * @return SLLKind
     */
    public String getSSLKind() {
        return mySSLKind;
    }

    /**
     * Only used for MSCAPI
     */
    public String getAlias() {
        return myAlias;
    }

    public String getCertificatePath() {
        return myCertificatePath;
    }

    public void setCertificatePath(String path) {
        path = formatCertificatePath(path);
        myCertificatePath = path;
    }

    public static boolean isCertificatePath(String path) {
        return SVNFileType.getType(new File(formatCertificatePath(path))) == SVNFileType.FILE;
    }

    public static String formatCertificatePath(String path) {
        path = new File(path).getAbsolutePath();
        path = SVNPathUtil.validateFilePath(path);
        if (SVNFileUtil.isWindows && path.length() >= 3 &&
                path.charAt(1) == ':' &&
                path.charAt(2) == '/' &&
                Character.isLowerCase(path.charAt(0))) {
            path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
        }
        return path;
    }
}