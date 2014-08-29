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

package org.smap.smapTask.android.tasks;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.smap.smapTask.android.listeners.TaskDownloaderListener;
import org.smap.smapTask.android.taskModel.FormLocator;
import org.smap.smapTask.android.taskModel.TaskResponse;
import org.smap.smapTask.android.utilities.ManageForm;
import org.smap.smapTask.android.utilities.ManageFormResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.Assignment;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.database.TaskAssignment;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Background task for downloading tasks 
 * 
 * @author Neil Penman (neilpenman@gmail.com)
 */
public class DownloadTasksTask extends AsyncTask<Void, String, HashMap<String, String>> {
    
	private TaskDownloaderListener mStateListener;
	HashMap<String, String> results = null;
    FileDbAdapter fda = new FileDbAdapter();
	Cursor taskListCursor = null;
	
	// class used to store status of existing tasks in the database and their database id
	private class TaskStatus {
		public long tid;
		public String status;
		public boolean keep;
		
		public TaskStatus(long tid, String status) {
			this.tid = tid;
			this.status = status;
			keep = false;
		}
	}
   
	/*
	 * Clean up after cancel
	 */
	@Override
	protected void onCancelled() {
    	if(fda != null) {
          	fda.close();
    	}
    	if(taskListCursor != null) {
    		taskListCursor.close();
    	}
	}
	
	@Override
    protected HashMap<String, String> doInBackground(Void... values) {
	
		results = new HashMap<String,String>();		

        fda.open();
        
        /*
         * Always remove local tasks that are no longer current
         */
        try {   	
            fda.deleteTasksFromSource("local", FileDbAdapter.STATUS_T_REJECTED);
            fda.deleteTasksFromSource("local", FileDbAdapter.STATUS_T_SUBMITTED);
        	
        } catch (Exception e) {		
            publishProgress("Database Error - Failed to remove local tasks");
    		e.printStackTrace();
        } finally {
        	fda.close();
        }
        
        
        // Check that the user has enabled task synchronisation
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(Collect.getInstance().getBaseContext());
        boolean tasksEnabled = settings.getBoolean("enable_tasks", false);
        Log.i("diag", "Tasks are enabled?" + tasksEnabled);
        synchronise(tasksEnabled);  
        
		
		return results;       
    }

    @Override
    protected void onPostExecute(HashMap<String, String> value) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.taskDownloadingComplete(value);
            }
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        synchronized (this) {
            if (mStateListener != null && values.length > 0) {
                mStateListener.progressUpdate(values[0]);
            }
        }

    }

    public void setDownloaderListener(TaskDownloaderListener sl, Context context) {
        synchronized (this) {
            mStateListener = sl;
        }
    }
  
  
    /*
     * Synchronise the tasks stored on the phone with those on the server
     *  There is an implicit assumption in some of the code that there can be multiple task management
     *  servers (or sources).  However the current implementation takes some shortcuts and assumes
     *  that there is a single remote source that is identified by the URL of the server that host the 
     *  surveys.
     *  
	 *  All database updates are within the scope of a transaction which is rolled back on an exception        
     */
    private void synchronise(boolean tasksEnabled) {
    	Log.i("diag", "Synchronise()");
    	int count = 0;
    	
    	String taskURL = null;
    	
        fda.open();
        fda.beginTransaction();					// Start Transaction
    	
        // Get the source (that is the location of the server that has tasks and forms)
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
        String username = settings.getString(PreferencesActivity.KEY_USERNAME, null);
        String password = settings.getString(PreferencesActivity.KEY_PASSWORD, null);
        Log.i("diag", "Source:" + source);
        
        if(source != null) {
	        try {
	        	
	        	cleanupTasks(fda, source);
	          	
	        	/*
	        	 * If tasks are enabled
	        	 * Get tasks for this source, that have already been downloaded. from the local database
	        	 * Add to a hashmap indexed on the source's task id
	        	 */
	            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels
	            
	        	HttpResponse getResponse = null;  
	        	DefaultHttpClient client = null;
	        	Gson gson = null;
	        	TaskResponse tr = null;
	        	int statusCode;
	        	if(tasksEnabled) {
		            HashMap<String, TaskStatus> taskMap = new HashMap<String, TaskStatus>();
		            taskListCursor = fda.fetchTasksForSource(source, false);
		            taskListCursor.moveToFirst();
		            while(!taskListCursor.isAfterLast()) {
		            	
			            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels
			            
		            	String status = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
		          	  	String aid = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ASSIGNMENTID));
		          	  	long tid = taskListCursor.getLong(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ID));
		          	  	TaskStatus t = new TaskStatus(tid, status);
		          	  	taskMap.put(aid, t);
		          	  	Log.i(getClass().getSimpleName(), "Current task:" + aid + " status:" + status);
		         		taskListCursor.moveToNext();
		            }
		            taskListCursor.close();
		             
		            // Get the tasks for this source from the server
		            client = new DefaultHttpClient();
		            
		            // Add credentials
		            if(username != null && password != null) {
		    	        client.getCredentialsProvider().setCredentials(
		    	                new AuthScope(null, -1, null),
		    	                new UsernamePasswordCredentials(username, password));
		            }
		            
		            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels
		            
		            // Call the service
		            taskURL = serverUrl + "/surveyKPI/myassignments";
		            InputStream is = null;
		            HttpGet getRequest = new HttpGet(taskURL);
	            	getResponse = client.execute(getRequest);
	            	statusCode = getResponse.getStatusLine().getStatusCode();
	            	if(statusCode != HttpStatus.SC_OK) {
	            		Log.w(getClass().getSimpleName(), "Error:" + statusCode + " for URL " + taskURL);
	            		throw new Exception("Error connecting - check username and password");
	            	} else {
	            		HttpEntity getResponseEntity = getResponse.getEntity();
	            		is = getResponseEntity.getContent();
	            	}
	     
	            	// De-serialise
	            	gson = new GsonBuilder().setDateFormat("dd/MM/yyyy hh:mm").create();
	            	Reader isReader = new InputStreamReader(is);
	            	tr = gson.fromJson(isReader, TaskResponse.class);
	            	Log.i(getClass().getSimpleName(), "Message:" + tr.message);
	            	
	            	// Synchronise forms
	            	synchroniseForms(tr.forms, serverUrl);
		            // Apply task changes
	            	count += addAndUpdateEntries(tr, fda, taskMap, username, source);
	        	}
	        	
            	/*
            	 * Loop through the entries in the database
            	 *  (1) Update on the server all that have a status of "accepted", "rejected" or "submitted" or "cancelled" or "completed"
            	 *      Note in the case of "cancelled" the client is merely acknowledging that it received the cancellation notice
            	 */
	            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels
	            
	        	if(tasksEnabled) {
		            updateTaskStatusToServer(fda, source, username, password, serverUrl);
	        	}
            	
	            /*
	             * Delete all orphaned tasks (The instance has been deleted)
	             */
	        	taskListCursor = fda.fetchAllTasks();
	            taskListCursor.moveToFirst();
	            while(!taskListCursor.isAfterLast()) {
	            	
		            if(isCancelled()) { throw new CancelException("cancelled"); };		// Return if the user cancels
		            
	          	  	String instancePath = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_INSTANCE));
	          	  	long tid = taskListCursor.getLong(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ID));
	          	  	Log.i(getClass().getSimpleName(), "Instance:" + instancePath);
	          	  	
	          	  	// Delete the task if the instance has been deleted
          	  		if(!instanceExists(instancePath)) {
          	  			fda.deleteTask(tid);
          	  		}
          	  		
          	  		taskListCursor.moveToNext();
	            }
	            taskListCursor.close();
	            
            	/*
            	 * Delete all entries in the database that are "Submitted" or "Rejected"
            	 * The user set these status values, no need to keep the tasks
            	 */
	            fda.deleteTasksFromSource(source, FileDbAdapter.STATUS_T_REJECTED);
	            fda.deleteTasksFromSource(source, FileDbAdapter.STATUS_T_SUBMITTED);
	            
	            
	            // Commit the transation
	            fda.setTransactionSuccessful();	// Commit the transaction
	            
	        } catch(JsonSyntaxException e) {
	        	
	        	Log.e(getClass().getSimpleName(), "JSON Syntax Error:" + " for URL " + taskURL);
	        	publishProgress(e.getMessage());
	        	e.printStackTrace();
	        	results.put("Error:", e.getMessage());
	        	
	        } catch (CancelException e) {	
	        	
	        	Log.i(getClass().getSimpleName(), "Info: Download cancelled by user."); 

	        } catch (Exception e) {	
	        	
	        	Log.e(getClass().getSimpleName(), "Error:" + " for URL " + taskURL);
	        	e.printStackTrace();
	        	publishProgress(e.getMessage());
	        	results.put("Error:", e.getMessage());
	
	        } finally {
	        	if(fda != null) {
	        		fda.endTransaction();
		          	fda.close();
	        	}
	        	if(taskListCursor != null) {
	        		taskListCursor.close();
	        		taskListCursor = null;
	        	}
	        }
        }
        
        if(count == 0) {
    		results.put("err_no_tasks", "");
        }
    }

    /*
	 * Delete all entries in the database that are "Missed" or "Cancelled
	 * These would have had their status set by the server the last time the user synchronised.  
	 * The user has seen their new status so time to remove.
	 */
    private void cleanupTasks(FileDbAdapter fda, String source) throws Exception {

    	Cursor taskListCursor = fda.fetchTasksForSource(source, false);
        taskListCursor.moveToFirst();
    	while(!taskListCursor.isAfterLast()) {
    		
    		if(isCancelled()) { return; };		// Return if the user cancels
    		
        	String status = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
      	    long id = taskListCursor.getLong(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ID));
	      	Log.i("cleanupTasks", "taskid:" + id + " -- status:" + status);          	    
        	if (status.equals(FileDbAdapter.STATUS_T_MISSED) || 
        			status.equals(FileDbAdapter.STATUS_T_CANCELLED)) {
        		fda.deleteTask(id);
        	}
    		taskListCursor.moveToNext();
    	}
    	taskListCursor.close();
		
	}

    /*
	 * Loop through the entries in the database
	 *  (1) Update on the server all that have a status of "accepted", "rejected" or "submitted"
	 */
	private void updateTaskStatusToServer(FileDbAdapter fda, String source, String username, String password,
			String serverUrl) throws Exception {
    	
		Log.i("updateTaskStatusToServer", "Enter");
		Cursor taskListCursor = fda.fetchTasksForSource(source, false);
        taskListCursor.moveToFirst();
        DefaultHttpClient client = new DefaultHttpClient();
        HttpResponse getResponse = null;
        
        // Add credentials
        if(username != null && password != null) {
	        client.getCredentialsProvider().setCredentials(
	                new AuthScope(null, -1, null),
	                new UsernamePasswordCredentials(username, password));
        }
        
        while(!taskListCursor.isAfterLast()) {
        	
    		if(isCancelled()) { return; };		// Return if the user cancels
    		
        	String newStatus = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
        	String syncStatus = taskListCursor.getString(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_IS_SYNC));
      	  	long aid = taskListCursor.getLong(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ASSIGNMENTID));
      	  	long tid = taskListCursor.getLong(taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ID));
      	  	
    		Log.i("updateTaskStatusToServer", "aId:" + aid + " -- status:" + newStatus + " -- syncStatus:" + syncStatus);
  	  		// Call the update service
  	  		if(newStatus != null && syncStatus.equals(FileDbAdapter.STATUS_SYNC_NO)) {
  	  			Log.i(getClass().getSimpleName(), "Updating server with status of " + aid + " to " + newStatus);
  	  			Assignment a = new Assignment();
  	  			a.assignment_id = (int)aid;
  	  			a.assignment_status = newStatus;
	            
	            // Call the service
	            String taskURL = serverUrl + "/surveyKPI/myassignments/" + aid;
	            HttpPost postRequest = new HttpPost(taskURL);
	            
	            ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
	            postParameters.add(new BasicNameValuePair("assignInput", "{assignment_status: "+ newStatus+ "}"));
	            
	            postRequest.setEntity(new UrlEncodedFormEntity(postParameters));
            	getResponse = client.execute(postRequest);
            	
            	int statusCode = getResponse.getStatusLine().getStatusCode();
            	
            	if(statusCode != HttpStatus.SC_OK) {
            		Log.w(getClass().getSimpleName(), "Error:" + statusCode + " for URL " + taskURL);
            	} else {
            		Log.w("updateTaskStatusToServer", "Status updated");
	            	fda.setTaskSynchronized(tid);		// Mark the task status as synchronised
            	}
  	  		}
      	  		
      	  	
     		taskListCursor.moveToNext();
        }
        taskListCursor.close();
		
	}
	
	/*
     * Loop through the entries from the source
     *   (1) Add entries that have a status of "new", "pending" or "accepted" and are not already on the phone
     *   (2) Update the status of database entries where the source status is set to "Missed" or "Cancelled"
     */
	private int addAndUpdateEntries(TaskResponse tr, FileDbAdapter fda, HashMap<String, TaskStatus> taskMap, String username, String source) throws Exception {
    	int count = 0; 
    	if(tr.taskAssignments == null) {
    		results.put("err_no_tasks", "");
    	} else {
        	for(TaskAssignment ta : tr.taskAssignments) {
	            
        		if(isCancelled()) { return count; };		// Return if the user cancels
	            
        		if(ta.task.type.equals("xform")) {
        			Assignment assignment = ta.assignment;
        			
    				Log.i(getClass().getSimpleName(), "Task: " + assignment.assignment_id + " Status:" + 
    						assignment.assignment_status + " Mode:" + ta.task.assignment_mode + 
    						" Address: " + ta.task.address + 
    						" Form: " + ta.task.form_id + " version: " + ta.task.form_version + 
    						" Type: " + ta.task.type + "Assignee: " + assignment.assignee + "Username: " + username);
            		
        			/*
        			 * The key for a task is actually the tasks assignment id
        			 * The same task could be assigned multiple times to a single user
        			 *  each time it will have a new assignment id
        			 */
        			// 
        			String uid = String.valueOf(assignment.assignment_id);	// Unique identifier for task from this source
	          	  	
            		// Find out if this task is already on the phone
	          	  	TaskStatus ts = taskMap.get(uid);
	          	  	if(ts == null) {
	          	  		Log.i(getClass().getSimpleName(), "New task: " + uid);
	          	  		// New task
	          	  		if(assignment.assignment_status.equals(FileDbAdapter.STATUS_T_NEW) ||
          	  						assignment.assignment_status.equals(FileDbAdapter.STATUS_T_PENDING) ||
          	  						assignment.assignment_status.equals(FileDbAdapter.STATUS_T_ACCEPTED)) {

	          	  			// Ensure the form and instance data are available on the phone
	          	  			// First make sure the initial_data url is sensible (ie null or a URL)
	          	  			if(ta.task.initial_data != null && !ta.task.initial_data.startsWith("http")) {
	          	  				ta.task.initial_data = null;	
	          	  			}
	          	  			
	                		if(isCancelled()) { return count; };		// Return if the user cancels
	                		
	          	  			// Add instance data
	          	  			ManageForm mf = new ManageForm();
	          	  			ManageFormResponse mfr = mf.insertInstance(ta.task.form_id, ta.task.form_version, ta.task.url, ta.task.initial_data, uid);
	          	  			if(!mfr.isError) {
	          	  				// Create the task entry
	          	  				fda.createTask(-1, source, ta, mfr.formPath, mfr.instancePath);
	          	  				results.put(uid + ":" + ta.task.title, "Created");
	          	  				count++;
	          	  			} else {
	          	  				results.put(uid + ":" + ta.task.title, "Creation failed: " + mfr.statusMsg );
	          	  				count++;
	          	  			}
	          	  		}
	          	  	} else {
	          	  		Log.i(getClass().getSimpleName(), "Existing Task: " + uid);
	          	  		// Existing task
	          	  		if(assignment.assignment_status.equals(FileDbAdapter.STATUS_T_MISSED)	|| 
          	  				assignment.assignment_status.equals(FileDbAdapter.STATUS_T_CANCELLED)) {
	          	  			fda.updateTaskStatusForAssignment(Long.parseLong(uid), assignment.assignment_status, source);
	          	  			results.put(uid + ":" + ta.task.title, assignment.assignment_status);
          	  				count++;
	          	  		} else { // check and update other details
	          	  			fda.updateTask(ta);
	          	  		}
	          	  	}

        			
        		}// end process for xform task
        	}// end tasks loop
    	}
    	
    	return count;
	}
	
	/*
     * Synchronise the forms on the server with those on the phone
     *   (1) Download forms on the server that are not on the phone
     *   (2) Delete forms not on the server or older versions of forms
     *       unless there is an uncompleted data instance using that form
     */
	private int synchroniseForms(List<FormLocator> forms, String serverUrl) throws Exception {
    	int count = 0; 
    	
    	System.out.println("Synchronise forms");
    	if(forms == null) {
    		results.put("err_no_forms", "");
    	} else {
    		
    		HashMap <String, String> formMap = new HashMap <String, String> ();
          	ManageForm mf = new ManageForm();
    		
        	for(FormLocator form : forms) {
	            
        		if(isCancelled()) { return count; };		// Return if the user cancels
	                		
        		// Set the form url from the 
        		form.url = serverUrl + "/formXML?key=" + form.ident;
        		
        		// Store a hashmap of new forms so we can delete existing forms not in the list
        		String entryHash = form.ident + "_v_" + form.version;
        		formMap.put(entryHash, entryHash);
        		
	          	// Download form and optionally instance data
	          	ManageFormResponse mfr = mf.insertForm(form.ident, form.version, form.url);	
        		
        	}
          	ManageFormResponse mfr = mf.deleteForms(formMap);
    	}
    	
    	return count;
	}

	/*
     * Return true if the passed in instance file is in the odk instance database
     * Assume that if it has been deleted from the database then it can't be sent although
     * it is probably still on the sdcard
     */
    boolean instanceExists(String instancePath) {
    	boolean exists = true;
    	
    	// Get the provider URI of the instance 
        String where = InstanceColumns.INSTANCE_FILE_PATH + "=?";
        String[] whereArgs = {
            instancePath
        };
        
    	ContentResolver cr = Collect.getInstance().getContentResolver();
		Cursor cInstanceProvider = cr.query(InstanceColumns.CONTENT_URI, 
				null, where, whereArgs, null);
		if(cInstanceProvider.getCount() != 1) {
			Log.e("MainListActivity:completeTask", "Unique instance not found: count is:" + 
					cInstanceProvider.getCount());
			exists = false;
		}
		cInstanceProvider.close();
    	return exists;
    }
}
