# Old Changelog

For newer versions, see [GitHub releases](https://github.com/jenkinsci/subversion-plugin/releases).

## Version 2.12.1 (Sep 20, 2018)

-   [JENKINS-48420](https://issues.jenkins-ci.org/browse/JENKINS-48420) Allow
    disabling lightweight checkout capability for Subversion with the
    property  -Djenkins.scm.impl.subversion.SubversionSCMFileSystem.disable=\<true/false\>

## Version 2.12.0 (Sep 17, 2018)

-   [JENKINS-48420](https://issues.jenkins-ci.org/browse/JENKINS-48420) Provide
    lightweight checkout capability for Subversion
-   [JENKINS-53325](https://issues.jenkins-ci.org/browse/JENKINS-53325) Use
    trilead classes from Jenkins core
-   [JENKINS-53615](https://issues.jenkins-ci.org/browse/JENKINS-53615) Attempt
    to (de-)serialize anonymous class
    hudson.scm.SubversionWorkspaceSelector$1

## Version 2.11.1 (Jul 11, 2018)

-   svnkit to
    1.9.3
-   [JENKINS-50327](https://issues.jenkins-ci.org/browse/JENKINS-50327) Update
    to 2.10.4 causes NullPointerException in
    HTTPDigestAuthentication.createDigest

## Version 2.11 (Jun 11, 2018)

-   [JENKINS-51817](https://issues.jenkins-ci.org/browse/JENKINS-51817) NPE
    throw in RemotableSVNErrorMessage due to wrong usage of C'tors

## Version 2.10.6 (May 17, 2018)

-   [JENKINS-45801](https://issues.jenkins-ci.org/browse/JENKINS-45801) Fetching
    changes fails with SVNAuthenticationException due many svn:externals
-   [JENKINS-49219](https://issues.jenkins-ci.org/browse/JENKINS-49219)[ Get
    'credentials \<none\> in realm' after svn
    update](https://issues.jenkins-ci.org/browse/JENKINS-50339)
-   [JENKINS-36451](https://issues.jenkins-ci.org/browse/JENKINS-36451)[ Repository
    Browser URL not saved in Project Pipeline
    Configuration](https://issues.jenkins-ci.org/browse/JENKINS-50339)
-    [JENKINS-50287](https://issues.jenkins-ci.org/browse/JENKINS-50287)
    Make as-it-is checkout only files initally
-    java
    8 required for presence of lambdas 
-    svnkit
    to 1.9.2
-    [JENKINS-30176](https://issues.jenkins-ci.org/browse/JENKINS-30176) Add
    VisualSVN Repository Browser to Subversion Plugin

## Version 2.10.5 (Mar 23, 2018)

-   [ JENKINS-50339](https://issues.jenkins-ci.org/browse/JENKINS-50339) log-entry:
    'svnkit-1.9.1.jar might be dangerous' after LTS update

## Version 2.10.4 (Mar 20, 2018)

-   [JENKINS-50287](https://issues.jenkins-ci.org/browse/JENKINS-50287) Make
    as-it-is checkout only files initally
-   [JENKINS-50288](https://issues.jenkins-ci.org/browse/JENKINS-50288) Add
    new option "cancelProcessOnExternalsFail" for ModuleLocation
-   [JENKINS-50289](https://issues.jenkins-ci.org/browse/JENKINS-50289) Update
    to SVNKit 1.9.1
-    [JENKINS-41626](https://issues.jenkins-ci.org/browse/JENKINS-41626) Branch
    indexing on subversion repo does not work properly
-    [JENKINS-49624](https://issues.jenkins-ci.org/browse/JENKINS-49624) Can’t
    select SVN credentials for shared library in folder using Modern SCM

## Version 2.10.3 (Feb 26, 2018)

-   [Fix security issue](https://jenkins.io/security/advisory/2018-02-26/)

## Version 2.10.2 (Dec 14, 2017)

-   [JENKINS-48546](https://issues.jenkins-ci.org/browse/JENKINS-48546) -
    Fix the binary compatibility issue, introduced
    by [JENKINS-14541](https://issues.jenkins-ci.org/browse/JENKINS-14541) as
    well

## Version 2.10.1 (Dec 12, 2017)

-   Fix the regression in 2.10 caused by
    [JENKINS-14541](https://issues.jenkins-ci.org/browse/JENKINS-14541)

## Version 2.10 (Dec 12, 2017)

-   JENKINS-14541
    Add support of the quiet checkout mode
-   JENKINS-14824
    Subversion Tag parametr now can be used in CLI
-   JENKINS-24802
    notifyCommit endpoint didn't trigger builds if an external without
    credentials with same "url start" is checked before the "main repo"
-   JENKINS-15376
    Prevent memory leak when using the "Subversion Tag" parameter
-   JENKINS-44956
    SVN repository browsers don't add hyperlinks for Pipeline jobs

-   https://github.com/jenkinsci/subversion-plugin/pull/190
    SVN Repository View now closes the repository session in the case of
    Runtime exception

-   https://github.com/jenkinsci/subversion-plugin/pull/192
    Streamline handling of credentials by using the [Credentials
    Plugin](https://wiki.jenkins.io/display/JENKINS/CloudBees+Credentials+Plugin)
    2.1.15 with new API

## Version 2.9 (July 10, 2017)

-   [Fix security
    issue](https://jenkins.io/security/advisory/2017-07-10/)

## Version 2.8 (Jun 16, 2017)

-   JENKINS-26100 Ability to define variables for Pipeline builds, when
    using Jenkins 2.60+ and a suitable version of Pipeline: SCM Step.
-   JENKINS-32302 `NullPointerException` handling credentials under
    certain circumstances.
-   JENKINS-42186 Memory leaks.
-   JENKINS-44076 Fix for affected paths field in changelog when using
    parameterized repository URLs.
-   JENKINS-32167 Logging about usage of credentials in SVN externals,
    to assist in diagnosing authentication issues. (Not claimed to be a
    fix for all issues.)
-   Support for Phabricator browser.
-   JENKINS-22112 Show committer info on build status page.
-   JENKINS-2717 No-op workspace updater.

## Version 2.7.2 (Mar 07, 2017)

-   [JENKINS-36521](https://issues.jenkins-ci.org/browse/JENKINS-36521)
    Print raw (not HTML-escaped) commit messages.

Pulls in [SCM API
Plugin](https://wiki.jenkins.io/display/JENKINS/SCM+API+Plugin) 2.x;
read [this blog
post](https://jenkins.io/blog/2017/01/17/scm-api-2/).

## Version 2.7.1 (Oct 18, 2016)

-   New SVNKit (1.8.14).
-   [JENKINS-31155](https://issues.jenkins-ci.org/browse/JENKINS-31155)
    [JENKINS-38048](https://issues.jenkins-ci.org/browse/JENKINS-38048)
    Support for Pipeline libraries.

## Version 2.6 (Jun 23, 2016)

-   Moved the Pipeline `svn` step to this plugin. **Note** that you must
    also update the *Pipeline: SCM Step* plugin at the same time.
-   Better support for Pipeline’s *Snippet Generator* in multibranch
    projects.
-   Fixed a reported hang in branch indexing for multibranch projects.

## Version 2.5.7 (Jan 6, 2016)

-   issue
    [\#32169](https://issues.jenkins-ci.org/browse/JENKINS-32169)
    NPE when using svn:// and file:// protocols in URL form validation
    (regression)

## Version 2.5.6 (Dec 30, 2015)

-   issue
    [\#27718](https://issues.jenkins-ci.org/browse/JENKINS-27718)
    Build parameter for "List Subversion Tags (and more)" are not
    exposed in a workflow script
-   issue
    [\#16711](https://issues.jenkins-ci.org/browse/JENKINS-16711)
    Improved handling of repositories (URL) with spaces
-   2 findbugs issues solved

## Version 2.5.5 (Dec 17, 2015)

-   issue
    [\#31385](https://issues.jenkins-ci.org/browse/JENKINS-31385)
    Additional message logs when svn:externals is used and
    SVNCancelException is thrown
-   issue
    [\#14155](https://issues.jenkins-ci.org/browse/JENKINS-14155)
    Automatically select and build the latest tag (via "List Subversion
    tags" build params)
-   issue
    [\#31192](https://issues.jenkins-ci.org/browse/JENKINS-31192)
    Subversion HTTPS + PKCS12 Certificate in CPU infinite loop
-   SVNKit library upgraded to 1.8.11. Special thanks to **Dmitry
    Pavlenko** (maintainer of SVNKit) for his help

## Version 2.5.4 (Nov 4, 2015)

-   issue
    [\#30774](https://issues.jenkins-ci.org/browse/JENKINS-30774) Subversion
    parameterized build throws error in job configuration. The global
    configuration option **Validate repository URLs up to the first
    variable name** has been removed.
-   issue
    [\#31067](https://issues.jenkins-ci.org/browse/JENKINS-31067) Subversion
    polling does not work when the repository URL contains a variable
    (global variable)
-   issue
    [\#24802](https://issues.jenkins-ci.org/browse/JENKINS-24802) notifyCommit
    don't trigger a build if two svn repositories have same URL start

## Version 2.5.3 (Sep 7, 2015)

-   issue
    [\#26264](https://issues.jenkins-ci.org/browse/JENKINS-26264)
    Cleanup AbortException call
-   issue
    [\#30197](https://issues.jenkins-ci.org/browse/JENKINS-30197)
    Migration from 1.x to 2.5.1 can fail (ClassCastException)

## Version 2.5.2 (Aug 19, 2015)

-   issue
    [\#26458](https://issues.jenkins-ci.org/browse/JENKINS-26458)
    E155021: This client is too old to work with the working copy
-   issue
    [\#29340](https://issues.jenkins-ci.org/browse/JENKINS-29340)
    E200015: ISVNAuthentication provider did not provide credentials
-   Findbugs was configured and
    [some](https://issues.jenkins-ci.org/browse/JENKINS-29492)
    issues solved

## Version 2.5.1 (Jul 8, 2015)

-   issue
    [\#29211](https://issues.jenkins-ci.org/browse/JENKINS-29211) E200015:
    ISVNAuthentication provider did not provide credentials
-   issue
    [\#29225](https://issues.jenkins-ci.org/browse/JENKINS-29225)
    Failed to tag
-   issue
    [\#20103](https://issues.jenkins-ci.org/browse/JENKINS-20103) ListTagParameter
    value doesn't show up in build API
-   issue
    [\#27084](https://issues.jenkins-ci.org/browse/JENKINS-27084) SVN
    authentication (NTLM) fails using Subversion Plugin v.2.5
-   [Update](https://github.com/jenkinsci/subversion-plugin/commit/b0351ea6bbbe0c471c66dcaa4e2dfa2914ef18d3)
    the version of Maven Release Plugin.

## Version 2.5 (Jan 2, 2015)

-   Replace custom SVNKit library in exchange for using the default
    binaries.
-   [JENKINS-18935](https://issues.jenkins-ci.org/browse/JENKINS-18935)
    Upgrade to svn 1.8.
-   [JENKINS-25241](https://issues.jenkins-ci.org/browse/JENKINS-25241)
    Upgrade trilead-ssh.

## Version 2.5-beta-4 (Oct 29, 2014)

-   [JENKINS-24341](https://issues.jenkins-ci.org/browse/JENKINS-24341)
    Check if the project can be disabled before the disabling
-   Fixes to form validation and pulldowns when used from unusual
    contexts, such as Workflow.
-   NPE when showing Workflow build index page.

## Version 2.5-beta-3 (Oct 08, 2014)

-   SECURITY-158 fix.
-   [JENKINS-22568](https://issues.jenkins-ci.org/browse/JENKINS-22568)
    Subversion polling does not work with parameters.
-   [JENKINS-18574](https://issues.jenkins-ci.org/browse/JENKINS-18574)
    [JENKINS-23146](https://issues.jenkins-ci.org/browse/JENKINS-23146)
    Fixed behavior for multiple locations in same repo.
-   [JENKINS-18534](https://issues.jenkins-ci.org/browse/JENKINS-18534)
    Prevent queue items with distinct Subversion tag parameters from
    being collapsed.

## Version 2.5-beta-2 (Jun 16, 2014)

(2.5-beta-1 botched due to upload error)

-   [JENKINS-23365](https://issues.jenkins-ci.org/browse/JENKINS-23365)
    Adapting to new SCM API in Jenkins 1.568+.

## Version 2.4.5 (Nov 10, 2014)

-   [JENKINS-25241](https://issues.jenkins-ci.org/browse/JENKINS-25241)
    SSH library fix needed for 1.585+.

## Version 2.4.4 (Oct 08, 2014)

-   SECURITY-158 fix.

## Version 2.4.3 (Aug 20, 2014)

-   [JENKINS-23146](https://issues.jenkins-ci.org/browse/JENKINS-23146)
    Revised fix from 2.4.2.

## Version 2.4.2 (Aug 18, 2014)

-   [JENKINS-18574](https://issues.jenkins-ci.org/browse/JENKINS-18574)
    [JENKINS-23146](https://issues.jenkins-ci.org/browse/JENKINS-23146)
    Fixed behavior for multiple locations in same repo.

## Version 2.4.1 (Jul 16, 2014)

-   [JENKINS-22568](https://issues.jenkins-ci.org/browse/JENKINS-22568)
    Subversion polling does not work with parameters.
-   [JENKINS-18534](https://issues.jenkins-ci.org/browse/JENKINS-18534)
    Prevent queue items with distinct Subversion tag parameters from
    being collapsed.

## Version 2.4 (May 16, 2014)

Known issues introduced in this version:
[JENKINS-23146](http://jenkins-ci.org/issue/23146)

-   svnexternals causing issue with concurrent builds ([issue
    15098](https://issues.jenkins-ci.org/browse/JENKINS-15098))
-   Fix additional credentials help (pull request 69)
-   changelog use external path for affected path, not workspace related
    ([issue
    18574](https://issues.jenkins-ci.org/browse/JENKINS-18574))
-   Call to doCheckRevisionPropertiesSupported broken ([issue
    22859](https://issues.jenkins-ci.org/browse/JENKINS-22859))
-   Subversion Parameters show "Repository URL" instead of the name
    ([issue
    22930](https://issues.jenkins-ci.org/browse/JENKINS-22930))
-   Plugin takes not excluded revprops from global configuration into
    account ([issue
    18099](https://issues.jenkins-ci.org/browse/JENKINS-18099))
-   Update MapDB dependency (for multi-branch project types support) to
    1.0.1
-   Change default of ignoreExternalsOption to true. Add help text
    explaining some of the security risks involved in checking out
    externals (namely that they can be a route to hijacking credentials
    that in most cases have full read access to the entire repository
    and not just the limited subset of the repository that an individual
    committer's credentials may have read access to. The recommended way
    to handle externals is to add those as additional modules directly.
    Thus ensuring that even if a committers machine is hacked or
    otherwise compromised, their credentials cannot be used to commit a
    modified build script and svn:external definition that allows the
    entire contents of the Subversion repository to be zipped up and
    FTP'd to a remote server)

## Version 2.3 (May 1, 2014)

**Note:** Version 2.0 contained a fix for
[JENKINS-18574](https://issues.jenkins-ci.org/browse/JENKINS-18574).
However, the fix caused 2 regressions, so it was reverted in this
version

-   Fixed authentication for externals in post-commit hook
    [JENKINS-21785](https://issues.jenkins-ci.org/browse/JENKINS-21785)
-   Fixed a regression which broke polling and checkout when using
    externals
    [JENKINS-22199](https://issues.jenkins-ci.org/browse/JENKINS-22199)
-   Fixed a regression causing broken changelog entries
    [JENKINS-22778](https://issues.jenkins-ci.org/browse/JENKINS-22778)
-   Fixed a NPE when running on slaves (See:
    <https://github.com/jenkinsci/subversion-plugin/commit/db5488140a75ddaa2ee56f428b3082854f46f942>)
-   Fixed an issue with the Subversion Tagging plugin (See:
    <https://github.com/jenkinsci/subversion-plugin/commit/c042204e09e87341fb08ff095cf3b873b7dec130>)

## Version 2.2 (Feb 18, 2014)

-   [issue \#21679 (side
    report)](https://issues.jenkins-ci.org/browse/JENKINS-21679) failure
    to find Subversion SCM descriptor if anonymous is missing the
    credential management permissions
-   (pull \#67) Improve List Subversion Tags parameter definition
    performance
-   (pull \#66) ListSubversionTagsParameterDefinition form validation
    broken

## Version 2.1 (Feb 11, 2014)

-   [JENKINS-21712](https://issues.jenkins-ci.org/browse/JENKINS-21712)
    requires an upgrade of the minimum version of the credentials plugin
    to 1.9.4 and a switch to using the \<c:select/\> jelly tag for
    selecting the credentials.
-   [JENKINS-21701](https://issues.jenkins-ci.org/browse/JENKINS-21701)
    Added credentials support for the tag parameter type.
-   Added credentials support for the tag action.
-   [JENKINS-18250](https://issues.jenkins-ci.org/browse/JENKINS-18250)
    (pull \#37) Improved authorization check for legacy
    (pre-credentials) authentication.
-   (pull \#60) Fixed post-commit cache.

## Version 2.0 (Jan 31, 2014)

-   Credentials management switched to credentials plugin based
    implementation. Upgrading to 2.0 will migrate your existing
    credentials from the semi-random scattered store that pre-2.0 used
    to the credentials plugin's store. You are advised to take a backup
    of your Jenkins instance if you have many jobs using different
    credentials against similar repositories. The following issues have
    been encountered by users upgrading.
    -   The same username required different passwords when accessing a
        repository from a windows slave or from a \*nix slave. Only one
        password (appears to be random as to which) was imported into
        the credentials store. Jobs that required the other password had
        their credentials reset to "none". Solution was to manually add
        the credential and configure the affected jobs to use the
        correct credentials.

## Version 1.54 (Nov 19, 2013)

-   prefer trilead-ssh from plugin vs core classloader ([issue
    \#19600](https://issues.jenkins-ci.org/browse/JENKINS-19600))
-   SVN\_REVISION is not exported if server url end by / with further
    @HEAD suffix ([issue
    \#20344](https://issues.jenkins-ci.org/browse/JENKINS-20344))
-   encryption of passphrases wasn't sufficiently secure ([issue
    \#SECURITY-58](https://issues.jenkins-ci.org/browse/SECURITY-58))

## Version 1.53 (Oct 15, 2013)

-   Expand environment variables in repository URLs ([pull request
    38](https://github.com/jenkinsci/subversion-plugin/pull/38))

## Version 1.52 (skipped)

Version does not exist

## Version 1.51 (Sep 15, 2013)

-   new depth option to leave --depth values as they are
    ([JENKINS-17974](https://issues.jenkins-ci.org/browse/JENKINS-17974))
-   Fixed support for pinned
    externals ([JENKINS-16533](https://issues.jenkins-ci.org/browse/JENKINS-16533))
-   Fixed: HTTPS with Client Certificates gives
    'handshake\_failure' ([JENKINS-19175](https://issues.jenkins-ci.org/browse/JENKINS-19175))
-   Upgrade to SVNKit 1.7.10 (minor bugfixes)

## Version 1.50 (Jun 02, 2013)

-   added files merged from a branch results in those files having
    doubled content
    ([JENKINS-14551](https://issues.jenkins-ci.org/browse/JENKINS-14551))

## Version 1.48 (May 20, 2013)

-   Use svn switch instead of checkout if changed module location stays
    within the same Subversion repository.
    ([JENKINS-2556](https://issues.jenkins-ci.org/browse/JENKINS-2556))
-   Fixed: change information was lost if build failed during svn
    checkout.
    ([JENKINS-16160](https://issues.jenkins-ci.org/browse/JENKINS-16160))

## Version 1.45 (Jan 22, 2013)

-   Update svnkit library to 1.7.6
-   New option to filter changelog based on the same inclusion/exclusion
    filters used to detect changes.
    ([JENKINS-10449](https://issues.jenkins-ci.org/browse/JENKINS-10449))
-   New options for ignoring externals and setting the repository depth
    ([JENKINS-777](https://issues.jenkins-ci.org/browse/JENKINS-777))
-   Fixed: support for svn:externals
    ([JENKINS-16217](https://issues.jenkins-ci.org/browse/JENKINS-16217)
    and
    [JENKINS-13790](https://issues.jenkins-ci.org/browse/JENKINS-13790))
-   Remove MailAddressResolverImpl that has serious negative impact on
    UI performance
    ([JENKINS-15440](https://issues.jenkins-ci.org/browse/JENKINS-15440)).
    Moved to dedicated plugin subversion-mail-address-resolver-plugin.

## Version 1.44 (Dec 16, 2012)

-   switch to ignore property-only changes on directories
    ([JENKINS-14685](https://issues.jenkins-ci.org/browse/JENKINS-14685))
-   support ignoring post-commit hooks (needs Jenkins 1.493+)
    ([JENKINS-6846](https://issues.jenkins-ci.org/browse/JENKINS-6846))
-   ignore disabled projects when evaluating post-commit notifications
    ([JENKINS-15794](https://issues.jenkins-ci.org/browse/JENKINS-15794))
-   fixed a problem with working copy cleanup under svn 1.7

## Version 1.43 (Sept 24, 2012)

-   additional logging about the revision being checked out
-   fail build on update/checkout failure
    ([JENKINS-14629](https://issues.jenkins-ci.org/browse/JENKINS-14629))
-   switch to fresh checkout on corrupted workspace detection -
    workaround for
    ([JENKINS-14550](https://issues.jenkins-ci.org/browse/JENKINS-14550))
-   support for Assembla browser

## Version 1.42 (June 22, 2012)

-   Fixed svn:external handling
    ([JENKINS-13790](https://issues.jenkins-ci.org/browse/JENKINS-13790))
-   Fixed a problem where SVNKit was dropping some of the chained
    exceptions
    ([JENKINS-13835](https://issues.jenkins-ci.org/browse/JENKINS-13835))

## Version 1.40 (May 11, 2012)

-   Supported Subversion 1.7
    ([JENKINS-11381](https://issues.jenkins-ci.org/browse/JENKINS-11381))

## Version 1.39 (Feb 16, 2012)

-   Fixed an invalid assumption in processing post-commit hooks, namely
    that all jobs are immediate children of the Jenkins singleton. For
    quite some time there has been an extension point that allows this
    assumption to be broken (the CloudBees Folders plugin is one example
    plugin that invalidates the assumption).

## Version 1.38 (Feb 14, 2012)

-   Fixed
    [JENKINS-10227](https://issues.jenkins-ci.org/browse/JENKINS-10227):
    Parameter "List subversion tag" was not displaying tags which have
    the same date.
-   Fixed
    [JENKINS-12201](https://issues.jenkins-ci.org/browse/JENKINS-12201):
    NullPointerException during SVN update.
-   Fixed
    [JENKINS-8036](https://issues.jenkins-ci.org/browse/JENKINS-8036):
    Subversion plugin to support SVN::Web browser.
-   Fixed
    [JENKINS-12525](https://issues.jenkins-ci.org/browse/JENKINS-12525):
    SVN URL converted to lowercase -\> Changes found every time it has
    an pull request.
-   Fixed
    [JENKINS-12113](https://issues.jenkins-ci.org/browse/JENKINS-12113):
    Local module directories weren't expanded with environment
    variables.

## Version 1.37 (Dec 2, 2011)

-   Fixed blocker bug in 1.36
    ([JENKINS-11901](https://issues.jenkins-ci.org/browse/JENKINS-11901)).

## Version 1.36 (NOT SAFE FOR USE)

-   Attention: this version contains a blocker
    bug; <https://issues.jenkins-ci.org/browse/JENKINS-11901> Don't use
    it!
-   Updated to SVNKit 1.3.6.1

## Version 1.35 (Nov 19, 2011)

-   Build aborted during checkout/update should be marked as ABORTED
    instead of FAILURE. Note: fix needs Jenkins 1.440 to be effective
    ([JENKINS-4605](https://issues.jenkins-ci.org/browse/JENKINS-4605)).
-   ListSubversionTagsParameterValue doesn't implement
    getShortDescription()
    ([JENKINS-11558](https://issues.jenkins-ci.org/browse/JENKINS-11558)).
-   Changelog shouldn't be duplicated because of svn:externals
    ([JENKINS-2344](https://issues.jenkins-ci.org/browse/JENKINS-2344)).

## Version 1.34 (Oct 31, 2011)

-   SCM build trigger not working correctly with variables in SVN URL
    ([JENKINS-10628](https://issues.jenkins-ci.org/browse/JENKINS-10628)).
-   Setting SVN credentials for a job should not require administer
    permission
    ([JENKINS-11415](https://issues.jenkins-ci.org/browse/JENKINS-11415)).
-   List Subversion tags: Tags filter were blank when re-configuring a
    job
    ([JENKINS-11055](https://issues.jenkins-ci.org/browse/JENKINS-11055))
-   When adding a new module location to an existing one, Jenkins
    re-checks out all module locations
    ([JENKINS-7461](https://issues.jenkins-ci.org/browse/JENKINS-7461)).
-   accept parameter for checkout to local module-path
    ([JENKINS-7136](https://issues.jenkins-ci.org/browse/JENKINS-7136)).

## Version 1.33 (Oct 11, 2011)

-   Broken link in help text of Subversion configuration
    ([JENKINS-11050](https://issues.jenkins-ci.org/browse/JENKINS-11050)).
-   tag this build is missing the tag url field
    ([JENKINS-10857](https://issues.jenkins-ci.org/browse/JENKINS-10857)).
-   Subversion no longer passes revision information to other plugins
    via
    SubversionTagAction([JENKINS-11032](https://issues.jenkins-ci.org/browse/JENKINS-11032)).
-   Subversion Tagging does not work correctly with alternate
    credentials([JENKINS-10461](https://issues.jenkins-ci.org/browse/JENKINS-10461)).
-   SVN post-commit hook doesn't work at root of repository
    ([JENKINS-11263](https://issues.jenkins-ci.org/browse/JENKINS-11263)).
-   SubversionChangeLogSet does now implement getCommitId()
    ([JENKINS-11027](https://issues.jenkins-ci.org/browse/JENKINS-11027)).

## Version 1.32 (Sep 15, 2011)

-   Increased functionality of **List Subversion tags** build parameter
    ([JENKINS-10678](https://issues.jenkins-ci.org/browse/JENKINS-10678))
-   Hudson mentioned in SVN credentials confirmation page
    ([JENKINS-10733](https://issues.jenkins-ci.org/browse/JENKINS-10733)).
-   Variables (build parameters and environment variables) are now
    expanded in **Local module directory**
    ([JENKINS-4547](https://issues.jenkins-ci.org/browse/JENKINS-4547))
-   SVN\_URL and SVN\_REVISION are not set when using @NNN in Subversion
    URL
    ([JENKINS-10942](https://issues.jenkins-ci.org/browse/JENKINS-10942))
-   Local module directory contains @NNN when @NNN is used in Subversion
    repository
    URL.([JENKINS-10943](https://issues.jenkins-ci.org/browse/JENKINS-10943))

## Version 1.31 (Aug 19, 2011)

-   URL that receives POST doesn't need to be protected. It is properly
    hardened to deal with malicious inputs.

## Version 1.31 (Aug 13, 2011)

-   Fixed a build error if build is running with different VM than
    Jenkins is
    ([JENKINS-10030](https://issues.jenkins-ci.org/browse/JENKINS-10030)).

## Version 1.30 (Aug 12, 2011)

-   Fixed the symlink handling problem with "emulate clean checkout"
    update strategy
    ([JENKINS-9856](https://issues.jenkins-ci.org/browse/JENKINS-9856))
-   Fixed a continuous polling bug
    ([JENKINS-6209](https://issues.jenkins-ci.org/browse/JENKINS-6209))

## Version 1.29 (July 24, 2011)

-   Order affected paths by name
    ([JENKINS-10324](https://issues.jenkins-ci.org/browse/JENKINS-10324)).
-   Blank build parameters not correctly handled in URLs
    ([JENKINS-10045](https://issues.jenkins-ci.org/browse/JENKINS-10045)).

## Version 1.28 (June 15, 2011)

-   Added a new **Sort newest first** option to **List Subversion tags**
    build parameters
    ([JENKINS-9828](https://issues.jenkins-ci.org/browse/JENKINS-9828)).
-   **Repository URL** is now expanded based on all environment
    variables (including build parameters) rather than just build
    parameters
    ([JENKINS-9925](https://issues.jenkins-ci.org/browse/JENKINS-9925)).

## Version 1.27 (June 7, 2011)

-   Added new **Tags filter** and **Sort Z-A** options to **List
    Subversion tags** build parameters
    ([JENKINS-9826](https://issues.jenkins-ci.org/browse/JENKINS-9826)).
-   Added global configuration opt-out for storing native command line
    authentication
    ([JENKINS-8059](https://issues.jenkins-ci.org/browse/JENKINS-8059)).

## Version 1.26 (May 10, 2011)

-   plugin breaks native svn command line authentication
    ([JENKINS-8059](https://issues.jenkins-ci.org/browse/JENKINS-8059)).
-   svn revision number returns an error
    ([JENKINS-9525](https://issues.jenkins-ci.org/browse/JENKINS-9525)).
-   replaced Hudson with Jenkins.

## Version 1.25 (Apr 1, 2011)

-   Check and Remove empty Repository URL from List
    ([JENKINS-9143](https://issues.jenkins-ci.org/browse/JENKINS-9143)).
-   1.24 had accidental Java6 dependency
    ([JENKINS-9222](https://issues.jenkins-ci.org/browse/JENKINS-9222)).

## Version 1.24 (Mar 22, 2011)

-   Added a new job parameter allowing to dynamically list svn tags.
-   Improved error diagnostics in case of failed WebDAV authentication

## Version 1.23 (Jan 6, 2011)

-   Introduced a new extension point to control the checkout behaviour.
-   Added a new checkout strategy that emulates "svn checkout" by "svn
    update" + file deletion.
-   Fixed revision number pinning problem
    ([JENKINS-8266](https://issues.jenkins-ci.org/browse/JENKINS-8266))
-   Fixed a per-job credential store persistence problem
    ([JENKINS-8061](https://issues.jenkins-ci.org/browse/JENKINS-8061))
-   Fixed a commit notify hook problem with Jetty
    ([JENKINS-8056](https://issues.jenkins-ci.org/browse/JENKINS-8056))

## Version 1.22 (Dec 10, 2010)

-   Support revision keywords such as HEAD in repository URL
    configurations.
-   Fixed StringOutOfBoundException.
    ([JENKINS-8142](https://issues.jenkins-ci.org/browse/JENKINS-8142))
-   Fixed "Unable to locate a login configuration" error with Windows
    Subversion server
    ([JENKINS-8153](https://issues.jenkins-ci.org/browse/JENKINS-8153))

## Version 1.21 (Nov 18, 2010)

-   Expose Subversion URL and revision information for all modules in
    environment variables
    ([JENKINS-3445](https://issues.jenkins-ci.org/browse/JENKINS-3445))
-   Added system property to control whether SCM polling runs on the
    Hudson master or on slaves
    ([JENKINS-5413](https://issues.jenkins-ci.org/browse/JENKINS-5413))
-   Make sure stored credentials have restricted file permissions

## Version 1.20 (Nov 1, 2010)

-   Fixed a serialization issue.

## Version 1.19 (Oct 29, 2010)

-   Fixed a configuration roundtrip regression introduced in 1.18
    ([JENKINS-7944](https://issues.jenkins-ci.org/browse/JENKINS-7944))
-   Supported svn:externals to files
    ([JENKINS-7539](https://issues.jenkins-ci.org/browse/JENKINS-7539))

## Version 1.18 (Oct 27, 2010)

-   **Requires Hudson 1.375 or newer.**
-   Builds triggered via the post-commit hook check out from the
    revision specified by the hook. The hook specifies the revision with
    either the rev query parameter, or the X-Hudson-Subversion-Revision
    HTTP header.
-   Uses svnkit 1.3.4 now.
    ([JENKINS-6417](https://issues.jenkins-ci.org/browse/JENKINS-6417))

## Version 1.17 (Apr 21, 2010)

-   Failure to retrieve remote revisions shouldn't result in a new build
    ([JENKINS-6136](https://issues.jenkins-ci.org/browse/JENKINS-6136))
-   Updated German & Japanese localization.
-   Fixed that "svn log copier thread" is nerver destoryed if exception
    is thrown while checking
    out.([JENKINS-6144](https://issues.jenkins-ci.org/browse/JENKINS-6144))

## Version 1.16 (Mar 23, 2010)

-   Fixed
    [JENKINS-6030](https://issues.jenkins-ci.org/browse/JENKINS-6030),
    where the new includedRegions feature broke the excludedRegions.

## Version 1.15 (Mar 22, 2010)

-   Added Spanish translation.

## Version 1.14 (Mar 17, 2010)

-   Add includedRegions feature, analogous to the excludedRegions
    feature
    ([JENKINS-5954](https://issues.jenkins-ci.org/browse/JENKINS-5954)).

## Version 1.13 (Mar 8, 2010)

-   Fixing polling for projects where last build was run on Hudson
    1.345+ with Subversion plugin 1.11 or older
    ([JENKINS-5827](https://issues.jenkins-ci.org/browse/JENKINS-5827))

## Version 1.12 (Mar 3, 2010)

-   Polling period can be set shorter than the quiet period now.
    ([JENKINS-2180](https://issues.jenkins-ci.org/browse/JENKINS-2180))
-   Exposed properties to the remote API.
    ([report](http://n4.nabble.com/Question-on-Hudson-API-td1566592.html#a1566592))
-   Validation for "excluded users" field was too restrictive.
    ([JENKINS-5684](https://issues.jenkins-ci.org/browse/JENKINS-5684))
-   Avoid ClassCastException in change log computation if job SCM is
    changed from Subversion to something else.
    ([JENKINS-5705](https://issues.jenkins-ci.org/browse/JENKINS-5705))

## Version 1.11 (Feb 11, 2010)

-   Allow commit comment to be provided when tagging.
    ([JENKINS-1725](https://issues.jenkins-ci.org/browse/JENKINS-1725))
-   Fix display of existing tags when there are multiple repositories.
-   Minor updates to aid in debugging problems triggering jobs from a
    post-commit hook.

## Version 1.10 (Jan 27, 2010)

-   SSL client certificate authentication was not working
    ([JENKINS-5349](https://issues.jenkins-ci.org/browse/JENKINS-5349))
-   Make all links from help text open a new window/tab, to avoid
    leaving the config page when there are unsaved changes
    ([JENKINS-5348](https://issues.jenkins-ci.org/browse/JENKINS-5348))
-   Export tag information via remote API
    ([JENKINS-882](https://issues.jenkins-ci.org/browse/JENKINS-882))

## Version 1.9 (Jan 16, 2010)

-   SSL client certificate authentication was not working
    ([JENKINS-5230](https://issues.jenkins-ci.org/browse/JENKINS-5230))
-   Tagging UI allows users to specify one-time credential for tagging
    ([JENKINS-2053](https://issues.jenkins-ci.org/browse/JENKINS-2053))
-   Fix a bug in the notifyCommit post-commit hook wrt a commit spanning
    multiple jobs
    ([JENKINS-4741](https://issues.jenkins-ci.org/browse/JENKINS-4741))

## Version 1.8 (Dec 23, 2009)

-   Polling can now ignore commits based on configurable keywords in the
    commit messages.
    ([patch](http://www.nabble.com/patch-for-subversion-plugin-to-add-exclusion-by-commit-message-td25288537.html))
-   Several minor bug fixes

## Version 1.7 (Sep 3, 2009)

-   Fixed a bug in the exclusion pattern matching
-   Fixed a bug in an interaction with the Trac plugin.
-   Fixed a bug in the interaction of concurrent builds and Subversion
    polling
    ([JENKINS-4270](https://issues.jenkins-ci.org/browse/JENKINS-4270))

## Version 1.6 (Aug 28, 2009)

-   Fixed a bug in polling on slaves
    ([JENKINS-4299](https://issues.jenkins-ci.org/browse/JENKINS-4299))
-   Fixed "authentication cancelled error" problem
    ([JENKINS-3936](https://issues.jenkins-ci.org/browse/JENKINS-3936))

## Version 1.5 (Aug 19, 2009)

-   Polling is performed on the slaves now
    ([report](http://www.nabble.com/svn-connection-from-slave-only-td24970587.html))
-   "Tag this Build" fails for 1.311+ with SVN Plugin
    ([JENKINS-4018](https://issues.jenkins-ci.org/browse/JENKINS-4018))

## Version 1.3 (July 8, 2009)

-   Subversion checkouts created files for symlinks
    ([JENKINS-3949](https://issues.jenkins-ci.org/browse/JENKINS-3949))

## Version 1.2 (June 24, 2009)

-   Fixed "endless authentication to SVN when invalid user/password"
    issue
    ([JENKINS-2909](https://issues.jenkins-ci.org/browse/JENKINS-2909))

## Version 1.0 (June 15, 2009)

-   Initial version split off from the core, to isolate SVNKit that has
    [a commercial-unfriendly
    license](http://svnkit.com/license.html).
