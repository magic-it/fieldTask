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

import java.lang.reflect.Type;
import java.util.ArrayList;

import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.smap.smapTask.android.R;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

public class TaskAddressActivity extends Activity implements OnClickListener {

	private class Address {
		String name;
		String value;
	}
	
	long taskId = -1;
	String userLon = null;
	String userLat = null;
	
	private static final int REJECT_BUTTON = 9000;
	private static final int ACCEPT_BUTTON = 9001;
	private static final int COMPLETE_BUTTON = 9002;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.task_address);  
        
        // Get the id of the selected list item
        Bundle bundle = getIntent().getExtras();
        taskId = bundle.getLong("id");
        userLon = bundle.getString("lon");
        userLat = bundle.getString("lat");
        
        Log.i("TaskAddressActivity", "Task Id: " + taskId);
        
      	FileDbAdapter fda = new FileDbAdapter();
      	Cursor c = null;
    	try {
    		fda.open();
    		c = fda.fetchTaskForId(taskId);

    		String taskStatus = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
    		String taskTitle = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_TITLE));
        	String taskAddress = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_ADDRESS));
        	
        	Log.i("Title:" + taskTitle, "Address:" + taskAddress);
        	
        	// Formatting
   			LinearLayout.LayoutParams textLayout = 
					new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			textLayout.setMargins(1, 1, 1, 1);
			
			TableRow.LayoutParams trLayout = 
					new TableRow.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			trLayout.setMargins(1, 1, 1, 1);
        	
            TableLayout tableLayout = (TableLayout)findViewById(R.id.task_address_values);
            
        	// Add Title
        	TextView title = (TextView)findViewById(R.id.task_title);
        	title.setText(taskTitle);
        	
        	// Add Status
   			TableRow r = new TableRow(this);
			r.setLayoutParams(trLayout);
			r.setBackgroundColor(0xff0000); 
	        TextView text1 = new TextView(this);
	        text1.setText("Status");
	        text1.setBackgroundColor(0xff0000);
	        TextView text2 = new TextView(this);
	        text2.setText(taskStatus);
	        text2.setBackgroundColor(0xff0000);
   	        r.addView(text1);
	        r.addView(text2);
	        tableLayout.addView(r);
	        
        	// Put the Address items in the table
    		Type type = new TypeToken<ArrayList<Address>>(){}.getType();		
    		ArrayList<Address> aArray = new Gson().fromJson(taskAddress, type);
    		if(aArray != null) {
	    		for(int i = 0; i < aArray.size(); i++) {
	    	        
	    			r = new TableRow(this);
	    			r.setLayoutParams(trLayout);
	    			r.setBackgroundColor(0xff0000);
	    			
	    	        
	    	        text1 = new TextView(this);
	    	        text1.setText(aArray.get(i).name);
	    	        text1.setBackgroundColor(0xff0000);
	    	        //text1.setGravity(android.view.Gravity.LEFT);
	    	        //text1.setLayoutParams(textLayout); 
	    	        
	    	        text2 = new TextView(this);
	    	        text2.setText(aArray.get(i).value);
	    	        text2.setBackgroundColor(0xff0000);
	    	        //text2.setGravity(android.view.Gravity.LEFT);
	    	        //text2.setLayoutParams(textLayout); 
	    	        
	    	        r.addView(text1);
	    	        r.addView(text2);
	    	        tableLayout.addView(r);
	    		}
    		}
    		
            // Create the buttons
            LinearLayout buttons = (LinearLayout)findViewById(R.id.task_address_buttons);
    		//menu.setHeaderTitle(taskTitle);
    		if(fda.canReject(taskStatus)) {

    	        Button b = new Button(this);
    	        b.setText("Reject Task");
    	        b.setId(REJECT_BUTTON);
    	        b.setOnClickListener(this);
    	        buttons.addView(b);
    		}
    		if(fda.canComplete(c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS)))) {
    	        Button b = new Button(this);
    	        b.setText("Complete Task");
    	        b.setId(COMPLETE_BUTTON);
    	        b.setOnClickListener(this);
    	        buttons.addView(b);
    		}
    		if(fda.canAccept(taskStatus)) {
    	        Button b = new Button(this);
    	        b.setText("Accept Task");
    	        b.setId(ACCEPT_BUTTON);
    	        b.setOnClickListener(this);
    	        buttons.addView(b);
    		}
			//menu.add(0,R.id.cancel_task,0,R.string.cancel);
    	} catch (Exception e) {
  			e.printStackTrace();
  	  	} finally {
  	  		if(fda != null) {
  	  			fda.close();
  	  		}
  	  		if(c != null) {
  	  			c.close();
  	  		}
  	  	}


    }

    /*
     * Handle a click on one of the buttons
     */
	@Override
	public void onClick(View v) {

      	FileDbAdapter fda = new FileDbAdapter();
      	Cursor c = null;
      	
        switch (v.getId()) {
        case ACCEPT_BUTTON:
        	try {
        		fda = new FileDbAdapter();
                fda.open();
                c = fda.fetchTaskForId(taskId);
                String taskStatus = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
        		if(fda.canAccept(taskStatus)) {
        			fda.updateTaskStatus(taskId, fda.STATUS_T_ACCEPTED);
        		} else {
        			Toast.makeText(getApplicationContext(), getString(R.string.smap_cannot_accept),
    		                Toast.LENGTH_SHORT).show();
        		}
        		fda.close();
        	} catch (Exception e) {
        		e.printStackTrace();
        	} finally {
        		if(fda != null) {
        			fda.close();
        		}
        		if(c != null) {
        			c.close();
        		}
        	}
        	finish();
            break;

        case COMPLETE_BUTTON:
    		try {   				

    			Log.i("Complete Button", "");
    	        fda = new FileDbAdapter();
    	        fda.open();
    			c = fda.fetchTaskForId(taskId);	
    			boolean canComplete = fda.canComplete(c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS)));
    			String taskForm = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_TASKFORM));
    			String formPath = Collect.FORMS_PATH + taskForm;
    			String instancePath = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_INSTANCE));
    			fda.close();
    			
    			if(canComplete) {
    				completeTask(instancePath, formPath, taskId);
    			} else {
        			Toast.makeText(getApplicationContext(), getString(R.string.smap_cannot_complete),
    		                Toast.LENGTH_SHORT).show();
    			}

    		} catch (Exception e) {
    			e.printStackTrace();
    		} finally {
    			if(fda != null) {
        			fda.close();
        		}
    			if(c != null) {
        			c.close();
        		}
    		}
    		break;
    		
        case REJECT_BUTTON:
        	try {
	            fda = new FileDbAdapter();
	            fda.open();
	            c = fda.fetchTaskForId(taskId);
	            String taskStatus = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
	    		if(fda.canReject(taskStatus)) {
	    			fda.updateTaskStatus(taskId, fda.STATUS_T_REJECTED);
	    		} else {
	    			Toast.makeText(getApplicationContext(), getString(R.string.smap_cannot_reject),
			                Toast.LENGTH_SHORT).show();
	    		}
	    		fda.close();

	    	} catch (Exception e) {
	    		e.printStackTrace();
	    	} finally {
	    		if(fda != null) {
	    			fda.close();
	    		}
	    		if(c != null) {
	    			c.close();
	    		}
	    	}
        	finish();
        	break;    	
 
        }
        return;
	}
	
	/*
	 * The user has selected an option to edit / complete a task
	 */
	public void completeTask(String instancePath, String formPath, long taskId) {
	
        FileDbAdapter fda = new FileDbAdapter();
        fda.open();
		// set the adhoc location
		try {
			fda = new FileDbAdapter();
			fda.open();
			fda.updateAdhocLocation(taskId, userLon, userLat);
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
}
