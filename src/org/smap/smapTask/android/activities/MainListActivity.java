/*
 * Copyright (C) 2011 Cloudtec Pty Ltd
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.smap.smapTask.android.activities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.smap.smapTask.android.R;
import org.smap.smapTask.android.adapters.TaskListCursorAdapter;
import org.smap.smapTask.android.listeners.TaskDownloaderListener;
import org.smap.smapTask.android.taskModel.UserDetail;
import org.smap.smapTask.android.tasks.DownloadTasksTask;

import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.activities.MainMenuActivity;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.DownloadFormListTask;
import org.odk.collect.android.utilities.CompatibilityUtils;
import org.odk.collect.android.application.Collect;

import com.nutiteq.components.WgsPoint;
import com.nutiteq.location.LocationListener;
import com.nutiteq.location.LocationSource;
import com.nutiteq.location.providers.AndroidGPSProvider;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Responsible for displaying buttons to launch the major activities. Launches some activities based
 * on returns of others.
 * 
 * @author Neil Penman 
 */
public class MainListActivity extends ListActivity 
								/*implements TaskDownloaderListener*/ {

    
	private AlertDialog mAlertDialog;
    public DownloadTasksTask mDownloadTasks;
	
	private Cursor taskListCursor;
	private TaskListCursorAdapter tasks;
  	private FileDbAdapter fda = null;
  	private LocationSource locationSource;
	private WgsPoint userLocation;
	
	// handler for received Intents for the "refresh" event 
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		  @Override
		  public void onReceive(Context context, Intent intent) {
			  refreshListView();
		  }
	};
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_list);
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.main_menu));
        
        setupListView();
        registerForContextMenu(getListView());

		locationSource = new AndroidGPSProvider(		// TODO move to application
				(LocationManager) getSystemService(Context.LOCATION_SERVICE), 1000L);
        locationSource.addLocationListener(new LocationListener() {
			
			@Override
			public void setLocation(WgsPoint point) {
				if (point != null) {
					userLocation = point;
				}
			}
		});
        locationSource.start();
        
        // Handle long item clicks
        ListView lv = getListView();
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
                return onLongListItemClick(v,pos,id);
            }
        });

    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onPause()
     */
    @Override
    protected void onPause() {
    	dismissDialogs();
    	// Unregister since the activity is not visible
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    	if (locationSource != null) {
    		locationSource.quit();
    	}
        super.onPause();
    }
    
    @Override
    protected void onDestroy() {
    	if (locationSource != null) {
    		locationSource.quit();
    	}
    	super.onDestroy();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();

    	if (locationSource != null) {
    		locationSource.start();
    	}
    	
    	// Register mMessageReceiver to receive messages. (from http://www.vogella.com/articles/AndroidBroadcastReceiver/article.html)
    	LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
    	      new IntentFilter("refresh"));
    	  
        refreshListView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
       
        return true;
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
   
    @Override
    protected void onStop() {
    	 try {
    	      super.onStop();

    	      if(taskListCursor != null) {
    	    	  taskListCursor.close();
    	    	  taskListCursor = null;
    	      }
    	      
    	    } catch (Exception e) {
    	     
    	    }
    }
    
    /*
     * Handle a long click on a list item
     */
    protected boolean onLongListItemClick(View v, int pos, long id) {
    	Log.i("onLongListItem", "id is:" + id);
    	Intent i = new Intent(getApplicationContext(), org.smap.smapTask.android.activities.TaskAddressActivity.class);
        i.putExtra("id", id);
        if(userLocation != null) {
        	i.putExtra("lon", String.valueOf(userLocation.getLon()));
        	i.putExtra("lat", String.valueOf(userLocation.getLat()));
        }
    	startActivity(i);
        return true;
    }
    
    /**
	 * Dismiss any showing dialogs that we manage.
	 */
	private void dismissDialogs() {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
	}
	    
    /*
     * Refresh the list view.
     */
    public void refreshListView() {
    
    	Log.i("refresh list view", "Enter");
    	try {
    		if(taskListCursor != null) {
	    		taskListCursor.close();
	    		taskListCursor = null;
    		}
            FileDbAdapter fda = new FileDbAdapter();
            fda.open();
    		taskListCursor = fda.fetchTasksForSource(getSource(), true);
        	fda.close();
    	} catch (Exception e) {
    		e.printStackTrace(); 	// TODO handle exception
    	}
    	tasks.changeCursor(taskListCursor);
    	startManagingCursor(taskListCursor);
    	tasks.notifyDataSetChanged();

    }
    
    /**
     * Retrieves task information from {@link TaskDbAdapter}, composes and displays each row.
     */
    private void setupListView() {

    	// get all tasks
        try {
            FileDbAdapter fda = new FileDbAdapter();
            fda.open();
        	taskListCursor = fda.fetchTasksForSource(getSource(), true);
        	fda.close();
        } catch (Exception e) {
        	e.printStackTrace(); 	// TODO handle exception
        }

        String[] data = new String[] {
                FileDbAdapter.KEY_T_STATUS, FileDbAdapter.KEY_T_TITLE, FileDbAdapter.KEY_T_SCHEDULED_START,
                FileDbAdapter.KEY_T_ADDRESS, FileDbAdapter.KEY_T_ID};
        
        int[] view = new int[] {R.id.icon, R.id.toptext, R.id.middletext, R.id.bottomtext};
  
        tasks = new TaskListCursorAdapter(this, R.layout.task_row, taskListCursor, data, view);  
        setListAdapter(tasks);
        startManagingCursor(taskListCursor);
    }
    
    /**
     * Starts executing the selected task
     */
    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
 
        // get full path to the instance
        Cursor c = (Cursor) getListAdapter().getItem(position);
        FileDbAdapter fda = new FileDbAdapter();
        fda.open();
		boolean canComplete = fda.canComplete(c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS)));
		fda.close();
        String taskForm = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_TASKFORM));
        String formPath = Collect.FORMS_PATH + taskForm;
        String instancePath = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_INSTANCE));
        long taskId = c.getLong(c.getColumnIndex(FileDbAdapter.KEY_T_ID));

        if(canComplete) {
        	completeTask(instancePath, formPath, taskId);
        } 
    }
 
	/*
	 * The user has selected an option to edit / complete a task
	 */
	public void completeTask(String instancePath, String formPath, long taskId) {
	
		// set the adhoc location
		try {
			fda = new FileDbAdapter();
			fda.open();
			if(userLocation != null) {
				fda.updateAdhocLocation(taskId, String.valueOf(userLocation.getLon()), String.valueOf(userLocation.getLat()));
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(fda != null) {
    			fda.close();
    		}
		}
		
		// Get the provider URI of the instance 
        String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
        String[] whereArgs = {
            instancePath
        };
		Cursor cInstanceProvider = managedQuery(InstanceColumns.CONTENT_URI, 
				null, where, whereArgs, null);
		if(cInstanceProvider.getCount() != 1) {
			Log.e("MainListActivity:completeTask", "Unique instance not found: count is:" + 
					cInstanceProvider.getCount());
		} else {
			cInstanceProvider.moveToFirst();
			Uri instanceUri = ContentUris.withAppendedId(InstanceColumns.CONTENT_URI,
	                cInstanceProvider.getLong(
	                cInstanceProvider.getColumnIndex(InstanceColumns._ID)));
			// Start activity to complete form
			Intent i = new Intent(Intent.ACTION_EDIT, instanceUri);

			i.putExtra(FormEntryActivity.KEY_FORMPATH, formPath);	// TODO Don't think this is needed
			i.putExtra(FormEntryActivity.KEY_TASK, taskId);			
			if(instancePath != null) {	// TODO Don't think this is needed
				i.putExtra(FormEntryActivity.KEY_INSTANCEPATH, instancePath);           
			}
			startActivity(i);
		} 
		cInstanceProvider.close();
	}
	
	/**
     * Creates an alert dialog with the given title and message. If shouldExit is set to true, the
     * activity will exit when the user clicks "ok".
     * 
     * @param title
     * @param message
     * @param shouldExit
     */
    private void createAlertDialog(String title, String message, final boolean shouldExit) {
        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "show");
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setTitle(title);
        mAlertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // ok
                        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "OK");
                        // successful download, so quit
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), quitListener);
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.show();
    }
    
    // Get the task source
    private String getSource() {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        String serverUrl = settings.getString(PreferencesActivity.KEY_SERVER_URL, null);
        String source = null;
        // Remove the protocol
        if(serverUrl.startsWith("http")) {
        	int idx = serverUrl.indexOf("//");
        	if(idx > 0) {
        		source = serverUrl.substring(idx + 2);
        	} else {
        		source = serverUrl;
        	}
        }
        
       return source;
    }

}


