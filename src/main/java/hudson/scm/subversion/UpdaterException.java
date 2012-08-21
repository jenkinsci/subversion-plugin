package hudson.scm.subversion;

import java.io.IOException;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class UpdaterException extends RuntimeException {

    public UpdaterException() {
    }

    public UpdaterException(String message) {
        super(message);
    }

    public UpdaterException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpdaterException(Throwable cause) {
        super(cause);
    }
}
