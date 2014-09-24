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

import java.util.List;

import loaders.SmapTaskLoader;
import loaders.TaskEntry;

import org.smap.smapTask.android.adapters.TaskListArrayAdapter;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;

import com.nutiteq.components.WgsPoint;
import com.nutiteq.location.LocationListener;
import com.nutiteq.location.LocationSource;
import com.nutiteq.location.providers.AndroidGPSProvider;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.smap.smapTask.android.R;

/**
 * Responsible for displaying buttons to launch the major activities. Launches some activities based
 * on returns of others.
 * 
 * @author Neil Penman 
 */
public class MainListActivity extends FragmentActivity  {
	
	private LoaderManager.LoaderCallbacks<List<TaskEntry>> mCallbacks;
  	private LocationSource locationSource;
	private WgsPoint userLocation;
	private AlertDialog mAlertDialog;
	
	
	 @Override
	  public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

	    // Create the ListFragment 
	    FragmentManager fm = getSupportFragmentManager();
	    if (fm.findFragmentById(android.R.id.content) == null) {
	      TaskListFragment list = new TaskListFragment();
	      fm.beginTransaction().add(android.R.id.content, list).commit();
	    }
	    
	    // Track location of user
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
	  }
	 
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	public void onPause() {
		dismissDialogs();

		if (locationSource != null) {
			locationSource.quit();
		}
	    super.onPause();
	}
	
	@Override
	public void onDestroy() {
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
	public void onResume() {
	    super.onResume();
	
		if (locationSource != null) {
			locationSource.start();
		}
		
		Intent intent = new Intent("refresh");
	    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		
	}
	
	@Override
	protected void onStop() {
		 try {
		      super.onStop();
		      
		    } catch (Exception e) {
		     
		    }
	}
	
	/**
	 * Dismiss any dialogs that we manage.
	 */
	private void dismissDialogs() {
		if (mAlertDialog != null && mAlertDialog.isShowing()) {
			mAlertDialog.dismiss();
		}
	}	  
	
	/*
	 * Fragment to display list of tasks
	 */
	 public static class TaskListFragment extends ListFragment implements
     		LoaderManager.LoaderCallbacks<List<TaskEntry>> { 
	
		private static final int TASK_LOADER_ID = 1;
		
		private TaskListArrayAdapter mAdapter;
	  	private FileDbAdapter fda = null;
	  	private MainListActivity mActivity;
		  	

	    public TaskListFragment() {
	    	super();    	
	    	mActivity = (MainListActivity) getActivity();
	    }
	    @Override
	    public void onActivityCreated(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        
	        mAdapter = new TaskListArrayAdapter(getActivity());
	        setListAdapter(mAdapter);
	        setListShown(false);
	        getLoaderManager().initLoader(TASK_LOADER_ID, null, this);
	        
	        registerForContextMenu(getListView());
	        
	        // Handle long item clicks
	        ListView lv = getListView();
	        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
	            @Override
	            public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
	                return onLongListItemClick(v,pos,id);
	            }
	        });
	
	    }
	    
	    @Override
	    public Loader<List<TaskEntry>> onCreateLoader(int id, Bundle args) {
	    	return new SmapTaskLoader(getActivity());
	    }
	
	    @Override
	    public void onLoadFinished(Loader<List<TaskEntry>> loader, List<TaskEntry> data) {
	    	mAdapter.setData(data);

	    	if (isResumed()) {
	    		setListShown(true);
	    	} else {
	    		setListShownNoAnimation(true);
	    	}
	    }
	
	    @Override
	    public void onLoaderReset(Loader<List<TaskEntry>> loader) {
	      mAdapter.setData(null);
	    }
	    
	    
	    
	    
	    @Override
	    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    	inflater.inflate(R.menu.task_context, menu);
	        super.onCreateOptionsMenu(menu, inflater);
	    }
	    
		
	    @Override
	    public boolean onOptionsItemSelected(MenuItem item) {
	        return super.onOptionsItemSelected(item);
	    }

	    
	    /*
	     * Handle a long click on a list item
	     */
	    protected boolean onLongListItemClick(View v, int position, long id) {
	    	
	    	TaskEntry task = (TaskEntry) getListAdapter().getItem(position);
	    	
	    	if(task.type.equals("task")) {
		    	Intent i = new Intent(getActivity(), org.smap.smapTask.android.activities.TaskAddressActivity.class);
		        i.putExtra("id", task.id);
		       
		        if(mActivity != null && mActivity.userLocation != null) {
		        	i.putExtra("lon", String.valueOf(mActivity.userLocation.getLon()));
		        	i.putExtra("lat", String.valueOf(mActivity.userLocation.getLat()));
		        }
		        
		    	startActivity(i);
	    	}
	        return true;
	    }
		    
	    
	    /**
	     * Starts executing the selected task
	     */
	    @Override
	    public void onListItemClick(ListView listView, View view, int position, long id) {
	 	       
	    	TaskEntry entry = (TaskEntry) getListAdapter().getItem(position);
	
	    	
	    	if(entry.type.equals("task")) {
	    		String formPath = Collect.FORMS_PATH + entry.taskForm;
	    		completeTask(entry.instancePath, formPath, entry.id, entry.status);
	    	} else {
	    		Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, entry.id);
	    		startActivity(new Intent(Intent.ACTION_EDIT, formUri));
	    	}

	    }
	 
		/*
		 * The user has selected an option to edit / complete a task
		 */
		public void completeTask(String instancePath, String formPath, long taskId, String status) {
		
			// set the adhoc location
			boolean canComplete = false;
			try {
				fda = new FileDbAdapter();
		        fda.open();	        
				canComplete = fda.canComplete(status);
				
				if(canComplete) {
					if(mActivity != null && mActivity.userLocation != null) {
						fda.updateAdhocLocation(taskId, String.valueOf(mActivity.userLocation.getLon()), 
								String.valueOf(mActivity.userLocation.getLat()));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if(fda != null) {
	    			fda.close();
	    		}
			}
			
			// Return if the user is not allowed to update this task
			if(!canComplete) {
				return;
			}
			
			// Get the provider URI of the instance 
	        String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
	        String[] whereArgs = {
	            instancePath
	        };
	       
			Cursor cInstanceProvider = Collect.getInstance().getContentResolver().query(InstanceColumns.CONTENT_URI, 
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
		

	    

	 }

}


