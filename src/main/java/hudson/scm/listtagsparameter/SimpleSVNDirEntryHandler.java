/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.scm.listtagsparameter;

import hudson.Util;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;

/**
 * Simple {@link ISVNDirEntryHandler} used to get a list containing all the
 * directories in a given Subversion repository.
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class SimpleSVNDirEntryHandler implements ISVNDirEntryHandler {

  private Map<Date, String> dirs = new HashMap<Date, String>();
  private Pattern filterPattern = null;

  public SimpleSVNDirEntryHandler(String filter) {
    if(StringUtils.isNotBlank(filter)) {
      filterPattern = Pattern.compile(filter);
    }
  }

  public List<String> getDirs() {
    return getDirs(false, false);
  }

  public List<String> getDirs(boolean reverseByDate, boolean reverseByName) {
    List sortedDirs = null;
    if(reverseByDate) {
      sortedDirs = new ArrayList();
      TreeSet<Date> keys = new TreeSet<Date>(dirs.keySet());
      for(Date key: keys) {
        sortedDirs.add(dirs.get(key));
      }
      Collections.reverse(sortedDirs);
    }
    else if(reverseByName) {
      sortedDirs = new ArrayList(dirs.values());
      Collections.reverse(sortedDirs);
    }
    else {
      sortedDirs = new ArrayList(dirs.values());
      Collections.sort(sortedDirs);
    }
    return sortedDirs;
  }

  public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
    if(filterPattern != null && filterPattern.matcher(dirEntry.getName()).matches() || filterPattern == null) {
      dirs.put(dirEntry.getDate(), Util.removeTrailingSlash(dirEntry.getName()));
    }
  }

}
