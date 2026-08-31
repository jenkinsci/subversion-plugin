This document describes building and testing Subversion plugin for Jenkins.

Jenkins plugins are **built normally using Maven**.
However, there is also a special process for running a plugin.

Note: HPI = Hudson Plugin Interface.

## (1) Building the plugin

Building should be possible both on Windows and Linux. You only need Maven and Java installed.

You should build with JDK LTS for testing. See:
https://en.wikipedia.org/wiki/Java_version_history#Release_table

### Environment config

Create `__setup_env.priv.sh` with your Java and Maven paths.

See more in: `__setup_env.example.sh`.

### Build

Building without tests from bash (or Git bash):
```bash
build-only.sh
```

The main artifact is:
```text
target/subversion.hpi
```

## (2) Testing

### Automated tests

```bash
build-full.sh
```

### Test Jenkins instance

You can run a local Jenkins instance with the local Subversion build. This is the best option for development.

```bash
build-run-local.sh
```

This should only take a few minutes. When the log becomes idle open:
http://localhost:8080/jenkins/

The working directory will be the `./work/` subdirectory.

To terminate the Jenkins instance use `CTRL`+`C` in the shell window.

### Testing in an existing Jenkins instance

Copy the file:
```text
target/subversion.hpi
```
to:
```text
$JENKINS_HOME/plugins/
```

Restart Jenkins.

## (3) Clean up after testing

If you modified an existing Jenkins instance you should remove the plugin after testing.

So after testing, remove:
```text
$JENKINS_HOME/plugins/subversion/
```
(the unpacked plugin directory)

This should force the plugin to be reinstalled.

However, in most cases it is probably better to stick to running a test Jenkins instance (`mvn hpi:run`).

## Adding automated tests

Files like `svn-externals.zip` are in the resources directory.

Example of creating a test job:
```java
        File testRepo = new CopyExisting(getClass().getResource("svn-externals.zip")).allocate();
        String repoUrl = "file://" + testRepo.toURI().toURL().getPath() + "repo/trunk";
        String localPath = "repo-dir";
        SubversionSCM scm = new SubversionSCM(repoUrl, localPath);
```
The function `testRepo.toURI().toURL().getPath()` will return a path like
`/target/tmp/j%20h1849784217265900740` (which will be the root of the ZIP file).
In the example above `repo` is a directory in the ZIP file which contains things like: `svn.ico`, `conf/`, `db/`.
And `trunk` is a path to a directory in the SVN repo (URI).


To make an svn checkout, just run the build (create an example project and build it):
```java
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm);
        var b = p.scheduleBuild2(0).get();
```

## Debug an automated test

To run or debug a single test, you might want to use NetBeans.

0. Open the project in NetBeans.
0. Open a test file, e.g. `src/test/java/hudson/scm/SubversionSCMTest.java`.
0. Scroll to a test, e.g. `workspaceVersionAndExternalFiles()`.
0. Use the play icon (<span style="color:green">▶</span>) to run or debug the test.

## Summary

- Dev build → `mvn clean install -DskipTests` XOR `build-only.sh`.
- Automatic tests → `mvn clean install` XOR `build-full.sh`.
- Dev test → `mvn hpi:run` XOR `build-run-local.sh`. Recommended for acceptance tests.
- Manual test → copy the `subversion.hpi` file to `plugins/` and restart Jenkins. Not recommended unless you need to test in a very specific environment.
