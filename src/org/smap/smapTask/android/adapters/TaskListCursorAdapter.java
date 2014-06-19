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

/*
 * Responsible for displaying tasks in a list view
 * 
 * @author Neil Penman (neilpenman@gmail.com)
 */
package org.smap.smapTask.android.adapters;

import java.text.DateFormat;

import org.smap.smapTask.android.R;
import org.smap.smapTask.android.utilities.KeyValueJsonFns;

import org.odk.collect.android.database.FileDbAdapter;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class TaskListCursorAdapter extends SimpleCursorAdapter {
    
    private int layout;
    LayoutInflater inflater;
	
    public TaskListCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
		super(context, layout, c, from, to);
		this.layout = layout;
		inflater = LayoutInflater.from(context);
	}
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
    	Cursor c = getCursor();

    	View v = inflater.inflate(layout, parent, false);
    
    	int statusCol = c.getColumnIndex(FileDbAdapter.KEY_T_STATUS);
    	String status = c.getString(statusCol);
    	ImageView icon = (ImageView) v.findViewById(R.id.icon);
    	if (status != null) {
    		if(status.equals("accepted")) {
    			icon.setImageResource(R.drawable.task_open);
    		} else if(status.equals("done")) {
    			icon.setImageResource(R.drawable.task_done);
    		} else if(status.equals("rejected") || status.equals("missed") || status.equals("cancelled")) {
    			icon.setImageResource(R.drawable.task_reject);
    		} else if(status.equals("new") || status.equals("pending") || status.equals("failed")) {
    			icon.setImageResource(R.drawable.task_new);
    		} else if(status.equals("submitted")) {
    			icon.setImageResource(R.drawable.task_submitted);
    		} else if(status.equals("missed")) {
    			icon.setImageResource(R.drawable.task_missed);
    		}
    	}
    	
    	int taskNameCol = c.getColumnIndex(FileDbAdapter.KEY_T_TITLE);
    	String taskName = c.getString(taskNameCol);
    	TextView taskNameText = (TextView) v.findViewById(R.id.toptext);
    	if (taskNameText != null) {
    		taskNameText.setText(taskName);
    	}

        DateFormat dFormat = DateFormat.getDateTimeInstance();
    	int taskStartCol = c.getColumnIndex(FileDbAdapter.KEY_T_SCHEDULED_START);
    	String taskStart = dFormat.format(c.getLong(taskStartCol));
    	TextView taskStartText = (TextView) v.findViewById(R.id.middletext);
    	if (taskStartText != null) {
    		taskStartText.setText(taskStart);
    	}
    	
    	int taskAddressCol = c.getColumnIndex(FileDbAdapter.KEY_T_ADDRESS);
    	String taskAddress = c.getString(taskAddressCol);
    	TextView taskAddressText = (TextView) v.findViewById(R.id.bottomtext);
    	if (taskAddressText != null) {
    		taskAddressText.setText(KeyValueJsonFns.getValues(taskAddress));
    	}
    	 
    	return v;
    }
    
    @Override
    public void bindView(View v, Context context, Cursor c) {

    	int statusCol = c.getColumnIndex(FileDbAdapter.KEY_T_STATUS);
    	String status = c.getString(statusCol);
    	ImageView icon = (ImageView) v.findViewById(R.id.icon);
    	if (status != null) {
    		if(status.equals("open")) {
    			icon.setImageResource(R.drawable.task_open);
    		} else if(status.equals("done")) {
    			icon.setImageResource(R.drawable.task_done);
    		} else if(status.equals("rejected")) {
    			icon.setImageResource(R.drawable.task_reject);
    		} else if(status.equals("new") || status.equals("failed")) {
    			icon.setImageResource(R.drawable.task_new);
    		} else if(status.equals("submitted")) {
    			icon.setImageResource(R.drawable.task_submitted);
    		}
    	}
    	
       	int taskNameCol = c.getColumnIndex(FileDbAdapter.KEY_T_TITLE);
    	String taskName = c.getString(taskNameCol);
    	TextView taskNameText = (TextView) v.findViewById(R.id.toptext);
    	if (taskNameText != null) {
    		taskNameText.setText(taskName);
    	}
    	
        DateFormat dFormat = DateFormat.getDateTimeInstance();
    	int taskStartCol = c.getColumnIndex(FileDbAdapter.KEY_T_SCHEDULED_START);
    	String taskStart = dFormat.format(c.getLong(taskStartCol));
    	TextView taskStartText = (TextView) v.findViewById(R.id.middletext);
    	if (taskStartText != null) {
    		taskStartText.setText(taskStart);
    	}
    	
    	int taskAddressCol = c.getColumnIndex(FileDbAdapter.KEY_T_ADDRESS);
    	String taskAddress = c.getString(taskAddressCol);
    	TextView taskAddressText = (TextView) v.findViewById(R.id.bottomtext);
    	if (taskAddressText != null) {
    		taskAddressText.setText(KeyValueJsonFns.getValues(taskAddress));
    	}
    }
}
