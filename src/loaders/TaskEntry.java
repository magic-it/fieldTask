package loaders;

import java.io.File;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;



/**
 * This class holds the per-item data in our {@link AppListLoader}.
 */
public class TaskEntry {
  private final SmapTaskLoader mLoader;
  public String type;	// form or task
  public String status;
  public String name;
  public String project;
  public String ident;
  public String taskStart;
  public String taskAddress;
  public String taskForm;
  public String instancePath;
  public int formVersion;
  public long id;

  public TaskEntry(SmapTaskLoader loader) {
    mLoader = loader;
  }


  @Override
  public String toString() {
    return status;
  }

}