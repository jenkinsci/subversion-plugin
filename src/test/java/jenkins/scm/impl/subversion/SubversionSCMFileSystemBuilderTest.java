package jenkins.scm.impl.subversion;

import hudson.scm.SubversionSCM;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubversionSCMFileSystemBuilderTest {

    @Test
    public void builderDisable(){
        System.setProperty(SubversionSCMFileSystem.DISABLE_PROPERTY, "true");
        SubversionSCMFileSystem.BuilderImpl builder = new SubversionSCMFileSystem.BuilderImpl();
        assertFalse(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertFalse(builder.supports(new SubversionSCMSource("id", "/")));
    }

    @Test
    public void builderEnable(){
        System.setProperty(SubversionSCMFileSystem.DISABLE_PROPERTY, "");
        SubversionSCMFileSystem.BuilderImpl builder = new SubversionSCMFileSystem.BuilderImpl();
        assertTrue(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertTrue(builder.supports(new SubversionSCMSource("id", "/")));

        System.setProperty(SubversionSCMFileSystem.DISABLE_PROPERTY, "false");
        builder = new SubversionSCMFileSystem.BuilderImpl();
        assertTrue(builder.supports(new SubversionSCM("svn://svn.example.com/trunk")));
        assertTrue(builder.supports(new SubversionSCMSource("id", "/")));
    }
}
