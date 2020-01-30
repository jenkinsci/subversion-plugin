# Introduction

This setup enables a Kerberos authentication for an Apache based Subversion server for your Jenkins Subversion SCM.

The setup was tested with a MS Active Directory 2012 R2 and 2008 R2 but should also work with other Directory servers. The Apache 2.4 on RHEL was configured with [mod_auth_gssapi](https://github.com/modauthgssapi/mod_auth_gssapi), part of the Linux distribution. In an older deployment mod_auth_kerb 5.4 was used successfully. The Jenkins agents were running on Windows 2016/2008/10/7 and different Linux distributions.

For Windows two different setups are explained: an agent server which is member of a domain and a standalone agent server without a domain membership.

# Prerequisites

- A domain account that has access to your Subversion server/repository.
- On Linux a Kerberos V5 installation and configuration. Only the MIT Kerberos has been tested.
- For Java 8 the JCE must be enabled - details below. For newer releases this is not necessary.
- For tests a native Subversion client is recommended.

### Enable Java Cryptography Extension (JCE)

Oracles Java 8 and older runtimes does not include encryption algorithms required by Kerberos due to U.S. export regulations. You must [download](https://www.oracle.com/technetwork/java/javase/downloads/jce-all-download-5170447.html) the JCE extension and install it. Follow the instructions in the package which are the same for Linux and Windows.

The same limitation applies to the Java distributions from IBM and the Open JDK, downloads are available.

### Server certificates

For HTTPS communication the Apache server is using a certificate. Make sure that the Certificate Authority (CA) of the server certificates is trusted by Java. As an alternative add the CA in the Subversion servers, parameter: ssl-authority-files.

# Prepare and test the domain account

**Important:** When the password for the service account was changed the keytab file must be re-created! It is possible configuring a policy that the password never exipres but this depends on the security policies of your organization.

### Linux

That the domain account is not compromised because the credentials are saved in clear text somewhere in the file system Kerberos is using a keytab file. In this file the domain credentials are stored encrypted. The keytab can be created by your domain administrator. When you have the password for the account and a Linux box you can create the keytab by yourself. Newer versions of directory servers are relying on strong encryption algorithms. The example below demonstrates the usage of arcfour-hmac for older releases and aes256-cts for newer releases. For the strong encryption the SALT value is required which can be determined like this:

    # get the SALT value
    $ KRB5_TRACE=/dev/tty kinit JenkinsAccount@DOMAIN.ORG
    ...
    Selected etype info: etype aes256-cts, salt "DOMAIN.ORGJenkinsAccount", params ""

**Advice:** The SALT value is supported since MIT Kerberso 1.17.

It is possible using both encryptions for a transition phase in one keytab, the Kerberos libraries will select the right on.

    $ ktutil
    ktutil: addent -password -p JenkinsAccount@DOMAIN.ORG -k 1 -e rc4-hmac
    Password for JenkinsAccount@DOMAIN.ORG: xxxxxx
    ktutil: addent -password -p JenkinsAccount@DOMAIN.ORG -k 1 -e aes256-cts -s DOMAIN.ORGJenkinsAccount
    Password for JenkinsAccount@DOMAIN.ORG: xxxxxx
    ktutil: wkt JenkinsAccount.keytab
    ktutil: q

Let’s have a look to the content of the keytab:

    $ klist -kte JenkinsAccount.keytab
    Keytab name: FILE: JenkinsAccount.keytab
    KVNO Timestamp           Principal
    ---- ------------------- ------------------------------------------------------
       1 30/01/2020 14:35:15 JenkinsAccount@DOMAIN.ORG (arcfour-hmac)
       1 30/01/2020 14:35:15 JenkinsAccount@DOMAIN.ORG (aes256-cts-hmac-sha1-96)

Use the keytab file to test the authentication, run the following command:

    $ kinit -kt JenkinsAccount.keytab JenkinsAccount@DOMAIN.ORG

When the run was successful (no output) let’s have a look to the created TGT:

    $ klist
    Ticket cache: DIR::/run/user/1000/krb5cc/tkt
    Default principal: JenkinsAccount@DOMAIN.ORG

    Valid starting       Expires              Service principal
    30/01/2020 15:59:30  30/01/2020 01:59:30  krbtgt/JenkinsAccount@DOMAIN.ORG
             renew until 30/01/2020 15:59:30

Test the access to the Subversion repository with a native Subversion client. Try to get the repository info:

    $ svn info https://svn.organization.org/repos/HelloWorld/trunk
    Path: trunk
    URL: https://svn.organization.org/repos/HelloWorld/trunk
    Relative URL: ^/trunk
    Repository Root: https://svn.organization.org/repos/HelloWorld
    Repository UUID: bd8deff7-301f-404f-b90d-11f05c129706
    Revision: 309
    Node Kind: directory
    Last Changed Author: JenkinsAccount@DOMAIN.ORG
    Last Changed Rev: 309
    Last Changed Date: 2020-01-20 18:52:10 +0100 (Sun, 13 Nov 2020)

### Windows - domain member

You can test the account and the access to the server/repository like this:

    > runas /user:DOMAIN.ORG\JenkinsAccount cmd

In the new opened cmd window run the tests (the output is truncated):

    > klist
    Client: JenkinsAccount @ DOMAIN.ORG
    Server: krbtgt/JenkinsAccount @ DOMAIN.ORG
    KerbTicket Encryption Type: AES-256-CTS-HMAC…
    ...

    > svn info https://svn.organization.org/repos/HelloWorld/trunk
    Path: trunk
    URL: https://svn.organization.org/repos/HelloWorld/trunk
    Relative URL: ^/trunk
    Repository Root: https://svn.organization.org/repos/HelloWorld
    Repository UUID: bd8deff7-301f-404f-b90d-11f05c129706
    ...

**TGT accessibility**

By default Windows does not allow the session key of a TGT to be accessed, add the following registry key.

The registry key and value should be:

    HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\Lsa\Kerberos
    Value Name: allowtgtsessionkey
    Value Type: REG_DWORD
    Value: 0x01

When this is not compliant with the security regulation of your company configure the build client in the same way like the standalone client.

### Windows - standalone client

The keytab should be created by the domain admin. Run the following commands to test the validity of the file. Both programs are part of the Java Runtime, do not use the klist program of Windows.

    > klist -kt C:\Jenkins\etc\JenkinsAccount.keytab
    Key tab: C:\Jenkins\etc\JenkinsAccount.keytab, 1 entry found.

    [1] Service principal: JenkinsAccount@DOMAIN.ORG
             KVNO: 1

    > kinit -t C:\Jenkins\etc\JenkinsAccount.keytab JenkinsAccount@DOMAIN.ORG
    New ticket is stored in cache file C:\Users\Jenkins\krb5cc_Jenkins

A test with a native Subversion client is not possible because it is not using the Java ticket cache!

# Setup of the Java Kerberos configuration file

Java needs some settings that Kerberos authentication works and they are placed in a file, e.g. JenkinsAccount.conf and this is the content.

### Linux and Windows standalone client:

    com.sun.security.jgss.krb5.initiate {
         com.sun.security.auth.module.Krb5LoginModule required
         useKeyTab=true
         keyTab="/home/jenkins/etc/JenkinsAccount.keytab"
         principal="JenkinsAccount@DOMAIN.ORG"
         debug=false
         ;
    };

You must replace the path for the `keyTab` file and the name for the `principal`. On Windows use `/` instead of `\`:
`C:/Jenkins/etc/JenkinsAccount.keytab`. Additional parameters should be not required.

### Windows domain client:

    com.sun.security.jgss.krb5.initiate {
         com.sun.security.auth.module.Krb5LoginModule required
         renewTGT=true
         doNotPrompt=true
         refreshKrb5Config=true
         useTicketCache=true
         debug=false
         ;
    };

# Configure the master/agent

The following parameters must be added to the Java configuration:

    -Djava.security.krb5.conf=/where-ever/krb5.conf
    -Dsun.security.krb5.debug=false
    -Djavax.security.auth.useSubjectCredsOnly=false
    -Djava.security.auth.login.config=/home/jenkins/etc/JenkinsAccount.conf

On Linux the first parameter is only required when the file is in another location than the default of your system. For Windows it must be specified. Do not use back slashes on Windows.

The `debug` parameter is optional, set to `true` for troubleshooting.

For the Jenkins master these parameters must be added to the Jenkins configuration. For an agent add them to the JVM Options under Advanced in the node configuration page.

Restart the master/agent.

# Configure the Jenkins job

Under Source Code Management / Subversion add just the URL of your repository and leave the credential empty.

**Note for master:** when you move the text pointer out of the text field, you will immediately see a red error message, in case your configuration does not work.

**Note for agent:** the authentication test every time will return an error. It looks like that this test is initiated on the master and not on the agent.

# Troubleshooting

- First make sure that the Kerberos authentication is woking with a native Subversion client. The client needs no special configuration. On Linux use only a client which is part of the distribution. Third party clients normally do not support  Kerberos, e.g. CollabNet Linux packages.
- You may try turning on debugging - use the debug parameter in the Java configuration file and sun.security.krb5.debug. Disable both after the issue is solved - the log files will grow rapidly.
- For a job running on the master check the Jenkins log file.
- For jobs running on an agent check the log of the agent and of the job.

# Some hints

- This setup works only when all jobs on the master or on an agent are using the same domain account for Subversion access. When different accounts are required it should be applicable to configure an agent for each domain account, even on the same computer. For a master this is not possible.
- This setup has not been tested with a Jenkins master running on Windows.
- This setup has not been tested with VisualSVN.
