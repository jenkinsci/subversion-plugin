package hudson.scm;

import hudson.scm.SubversionChangeLogSet.LogEntry;


public class SubversionChangeLogUtil {

  static SubversionChangeLogSet.LogEntry buildChangeLogEntry(int revision, String msg) {
      SubversionChangeLogSet.LogEntry entry = new SubversionChangeLogSet.LogEntry();
      entry.setRevision(revision);
      entry.setMsg(msg);
      return entry;
  }

}
