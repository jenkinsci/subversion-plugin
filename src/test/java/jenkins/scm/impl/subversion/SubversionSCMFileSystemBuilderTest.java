package jenkins.scm.impl.subversion;

import hudson.scm.SubversionSCM;
import org.junit.After;
import org.junit.Test;

import static jenkins.scm.impl.subversion.SubversionSCMFileSystem.DISABLE_PROPERTY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubversionSCMFileSystemBuilderTest {

    @After
    public void after(){
        System.clearProperty(DISABLE_PROPERTY);
    }

    @Test
    public void builderDisable(){
        System.setProperty(DISABLE_PROPERTY, "true");
        SubversionSCMFileSystem.BuilderImpl builder = new SubversionSCMFileSystem.BuilderImpl();
        assertFalse(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertFalse(builder.supports(new SubversionSCMSource("id", "/")));
    }

    @Test
    public void builderEnable(){
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
