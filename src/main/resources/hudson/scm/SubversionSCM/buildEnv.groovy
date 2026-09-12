package hudson.scm.SubversionSCM

def l = namespace(lib.JenkinsTagLib)

["SVN_REVISION","SVN_URL","SVN_REVISION_1","SVN_URL_1","SVN_REVISION_MAX","SVN_URL_MAX"].each { name ->
    l.buildEnvVar(name:name) {
        raw(_("${name}.blurb"))
    }
}
