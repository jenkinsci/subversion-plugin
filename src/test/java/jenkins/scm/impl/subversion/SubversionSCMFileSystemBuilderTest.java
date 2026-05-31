package jenkins.scm.impl.subversion;

import hudson.scm.SubversionSCM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static jenkins.scm.impl.subversion.SubversionSCMFileSystem.DISABLE_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubversionSCMFileSystemBuilderTest {

    @AfterEach
    void afterEach() {
        System.clearProperty(DISABLE_PROPERTY);
    }

    @Test
    void builderDisable() {
        System.setProperty(DISABLE_PROPERTY, "true");
        SubversionSCMFileSystem.BuilderImpl builder = new SubversionSCMFileSystem.BuilderImpl();
        assertFalse(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertFalse(builder.supports(new SubversionSCMSource("id", "/")));
    }

    @Test
    void builderEnable() {
        System.clearProperty(DISABLE_PROPERTY);
        SubversionSCMFileSystem.BuilderImpl builder = new SubversionSCMFileSystem.BuilderImpl();
        assertTrue(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertTrue(builder.supports(new SubversionSCMSource("id", "/")));

        System.setProperty(DISABLE_PROPERTY, "false");
        builder = new SubversionSCMFileSystem.BuilderImpl();
        assertTrue(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertTrue(builder.supports(new SubversionSCMSource("id", "/")));
    }
}
