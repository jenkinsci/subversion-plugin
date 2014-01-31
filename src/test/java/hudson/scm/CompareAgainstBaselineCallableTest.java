package hudson.scm;

import static java.util.regex.Pattern.compile;
import hudson.scm.SubversionSCM.SVNLogHandler;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.io.NullOutputStream;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;

public class CompareAgainstBaselineCallableTest {
    
    private CompareAgainstBaselineCallable callable;

    @Test
    public void callableShouldBeRemotable() throws IOException {
        givenACallable();
        
        // WHEN callable is serialized
        ObjectOutputStream oos = new ObjectOutputStream(new NullOutputStream());
        oos.writeObject(callable);
        // THEN no NotSerializableException should have been thrown
        oos.close();
    }

    private void givenACallable() {
        @SuppressWarnings("unchecked")
        SVNLogFilter filter = new DefaultSVNLogFilter(new Pattern[] {compile("excludes")}, new Pattern[] {compile("includes")},
                Collections.EMPTY_SET, "", new Pattern[0], false);
        
        StreamTaskListener taskListener = null; // this fails with NPE because of static Channel current(): StreamTaskListener.fromStdout();
        this.callable = new CompareAgainstBaselineCallable(
                new SVNRevisionState(null),
                new SVNLogHandler( filter, taskListener),
                        "projectName", taskListener, null, Collections.<String,ISVNAuthenticationProvider>emptyMap(), "nodeName");
    }

}
