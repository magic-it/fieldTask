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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import org.smap.smapTask.android.R;

import org.odk.collect.android.activities.DataManagerList;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.activities.FormManagerList;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.tasks.DownloadFormsTask;

import android.app.Activity;
import android.app.TabActivity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

/**
 * Submits the results of the requested instance
 */
public class SubmitResults extends Activity {

    private Uri mUri;
    boolean isError = false;
    String statusMsg = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        /*
         * 1. Get the parameters (form name, form URL, initial instance data) 
         * 2. Get the form path from the forms database using the taskId as the form name
         *      b) If this does not exist download the form using the form URL
         *         The requesteed taskId is dicarded and does not have to match the id of the dwonloaded form
         * 3. Get the instance path
         * 4. Write the instance initial data to the sdcard
         * 5. Create the new instance entry in the content provider
         */     
        String taskId = null;
        String formURL = null;
        String initialDataURL = null;
        
        String formName = null;
        String formPath = null;
        String submissionUri = null;
        String instancePath = null;

        
         // Get the input parameters
        Bundle extras = getIntent().getExtras();
		if (extras != null) {
			taskId = extras.getString("taskId");
			formURL = extras.getString("formURL");
			initialDataURL = extras.getString("initialDataURL");
		}  
		
		Log.i("InstanceCreate taskid", taskId);
		Log.i("InstanceCreate formURL", formURL);
		Log.i("InstanceCreate instanceData", initialDataURL);
        
		// Get the form details
   	 	Cursor c = null;		 
        try {
        	String selectionClause = FormsColumns.JR_FORM_ID + " = ?";
        	String [] selectionArgs = new String [1];
        	selectionArgs[0] = taskId;
        	String [] proj = {FormsColumns._ID, FormsColumns.DISPLAY_NAME, FormsColumns.JR_FORM_ID,
        			FormsColumns.SUBMISSION_URI,FormsColumns.FORM_FILE_PATH}; 
        	
        	c = managedQuery(FormsColumns.CONTENT_URI, proj, selectionClause, selectionArgs, null);
            if(c.getCount() > 0) {
	        	 c.moveToFirst();
	        	 String table_id = c.getString(c.getColumnIndex(FormsColumns._ID));
	             formName = c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME));
	             String jr = c.getString(c.getColumnIndex(FormsColumns.JR_FORM_ID));
	             submissionUri = c.getString(c.getColumnIndex(FormsColumns.SUBMISSION_URI));
	             formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
	             Log.i("InstanceCreate form cursor:", table_id +  
	            		 "--" + jr + "--" + formName + "--" + 
	            		 submissionUri + "--" + formPath);
             } else {
            	 File dl = null;
            	 
            	 try {
            		 Log.i("InstanceCreate", "Downloading form");
            		 DownloadFormsTask dft = new DownloadFormsTask();
            		 dl = dft.downloadXform(taskId, formURL);
            	 } catch (Exception e) {
            		 isError = true;
            		 statusMsg = "Unable to download form from " + formURL;
            		 return;
            	 }
            	 

            	 try {
            		 Log.i("InstanceCreate", "Inserting new form into forms database");
            		 ContentValues v = new ContentValues();
            		 v.put(FormsColumns.FORM_FILE_PATH, dl.getAbsolutePath());
	
	                 HashMap<String, String> formInfo = FileUtils.parseXML(dl);
	                 v.put(FormsColumns.DISPLAY_NAME, formInfo.get(FileUtils.TITLE));
	                 v.put(FormsColumns.JR_VERSION, formInfo.get(FileUtils.VERSION));
	                 v.put(FormsColumns.JR_FORM_ID, formInfo.get(FileUtils.FORMID));
	                 v.put(FormsColumns.SUBMISSION_URI, formInfo.get(FileUtils.SUBMISSIONURI));
	                 //v.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY));
	                  
	                 formName = formInfo.get(FileUtils.TITLE);
	                 submissionUri = formInfo.get(FileUtils.SUBMISSIONURI);
	                 formPath = dl.getAbsolutePath();
	                 taskId = formInfo.get(FileUtils.FORMID);
	                 
	                 /*
	                  * Before inserting make sure this form isn't alreadu in the database
	                  *  Although we have tested the database for the taskId previously, it is
	                  *  possible that the form URL was pointing to a form with a different id
	                  */
	                selectionClause = FormsColumns.JR_FORM_ID + " = ?";
	             	selectionArgs = new String [1];
	             	selectionArgs[0] = taskId;
	                c = managedQuery(FormsColumns.CONTENT_URI, proj, selectionClause, selectionArgs, null);
	                if(c.getCount() == 0) {
	                	Log.i("InstanceCreate", "Form does not already exist, hence inserting now");
	                	Collect.getInstance().getContentResolver().insert(FormsColumns.CONTENT_URI, v);
	                }
	                 
            	 } catch (Throwable e) {
               		 isError = true;
            		 statusMsg = "Unable to insert form from " + formURL + " into form database.";
            		 return;
            	 }
            	 
            	 
             }
             
        } catch (Exception e) {
        	e.printStackTrace();
        } finally {
         	if ( c != null ) {
         		c.close();
         	}
         }
         
  		 // Get the instance path
         instancePath = getInstancePath(formPath);
         if(instancePath != null) {
        	 Log.i("InstanceCreate Instance Path:", instancePath);
         }
         
         // Write the instance initial data to the sdcard
         // TODO
		    
         // Write the new instance entry in the content provider
         try {
			writeInstanceDatabase(taskId, formName, submissionUri, instancePath);
		} catch (Throwable e) {
			e.printStackTrace();
      		 isError = true;
    		 statusMsg = "Unable to insert form " + taskId + " into instance database.";
    		 return;
		}
         
        finish();
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#finish()
     * Return data to calling application
     */
    @Override
    public void finish() {
    	Intent data = new Intent();
    	
    	if(isError) {
    		
	    	data.putExtra("status", "error");
	    	data.putExtra("message", statusMsg);
    		
    	} else {
    		
	    	String instanceUri = mUri.toString();	    		
	    	data.putExtra("status", "success");
	    	data.putExtra("instanceUri", instanceUri);
	    	
    	}
    	setResult(RESULT_OK, data);
    	super.finish();
    }
    
    private String getInstancePath(String formPath) {
        String instancePath = null;
        
        if(formPath != null) {
	    	String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
	                        .format(Calendar.getInstance().getTime());
            String file =
                formPath.substring(formPath.lastIndexOf('/') + 1, formPath.lastIndexOf('.'));
            String path = Collect.INSTANCES_PATH + "/" + file + "_" + time;
            if (FileUtils.createFolder(path)) {
                instancePath = path + "/" + file + "_" + time + ".xml";
            }
        }
            
        return instancePath;
    }
    
    private void writeInstanceDatabase(String jrformid, String formName, 
    			String submissionUri, String instancePath) throws Throwable {
        
    	Log.i("InstanceCreate", "Inserting new instance into database");
    	ContentValues values = new ContentValues();
    	 
    	values.put(InstanceColumns.JR_FORM_ID, jrformid);
        values.put(InstanceColumns.SUBMISSION_URI, submissionUri);
        values.put(InstanceColumns.INSTANCE_FILE_PATH, instancePath);
        values.put(InstanceColumns.DISPLAY_NAME, formName);
        values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
        //values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(false));
    	
        mUri = Collect.getInstance().getContentResolver()
        			.insert(InstanceColumns.CONTENT_URI, values);

    }

}
