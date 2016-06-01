package hudson.scm.subversion;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
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
