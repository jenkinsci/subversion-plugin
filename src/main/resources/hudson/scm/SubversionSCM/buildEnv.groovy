package hudson.scm.SubversionSCM;

def l = namespace(lib.JenkinsTagLib)

["SVN_REVISION","SVN_URL"].each { name ->
    l.buildEnvVar(name:name) {
        raw(_("${name}.blurb"))
    }
}
