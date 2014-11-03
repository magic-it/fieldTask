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

/**
 * Responsible for managing the tabs on the main screen.
 * 
 * @author Neil Penman 
 */

package org.smap.smapTask.android.activities;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.listeners.FormDownloaderListener;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.logic.FormDetails;
import org.odk.collect.android.preferences.AdminPreferencesActivity;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.utilities.CompatibilityUtils;
import org.smap.smapTask.android.R;
import org.smap.smapTask.android.listeners.TaskDownloaderListener;
import org.smap.smapTask.android.tasks.DownloadTasksTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

public class MainTabsActivity extends TabActivity implements 
		TaskDownloaderListener, 
		InstanceUploaderListener,
		FormDownloaderListener{
	
    private AlertDialog mAlertDialog;
    private static final int PROGRESS_DIALOG = 1;
    private static final int ALERT_DIALOG = 2;
	private static final int PASSWORD_DIALOG = 3;
    
 // request codes for returning chosen form to main menu.
    private static final int FORM_CHOOSER = 0;
    private static final int INSTANCE_UPLOADER = 2;
    
    private static final int MENU_PREFERENCES = Menu.FIRST;
	private static final int MENU_ADMIN = Menu.FIRST + 1;
    private static final int MENU_ENTERDATA = Menu.FIRST + 2;
    private static final int MENU_MANAGEFILES = Menu.FIRST + 3;
    private static final int MENU_SENDDATA = Menu.FIRST + 4;
    private static final int MENU_GETTASKS = Menu.FIRST + 5;
    private static final int MENU_GETFORMS = Menu.FIRST + 6;

    private String mProgressMsg;
    private String mAlertMsg;
    private ProgressDialog mProgressDialog;  
    public DownloadTasksTask mDownloadTasks;
	private Context mContext;
	private SharedPreferences mAdminPreferences;
	
	private TextView mTVFF;
	private TextView mTVDF;
    
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    

        // must be at the beginning of any activity that can be called from an external intent
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), true);
            return;
        }
        
	    setContentView(R.layout.main_tabs);

	    Resources res = getResources(); // Resource object to get Drawables
	    TabHost tabHost = getTabHost();  // The activity TabHost
	    TabHost.TabSpec spec;  
	    Intent intent;  

		tabHost.setBackgroundColor(Color.WHITE);
		tabHost.getTabWidget().setBackgroundColor(Color.DKGRAY);
		
	    // Initialise a TabSpec and intent for each tab and add it to the TabHost
	    intent = new Intent().setClass(this, MainListActivity.class);    
	    spec = tabHost.newTabSpec("taskList").setIndicator(getString(R.string.smap_taskList)).setContent(intent);
	    tabHost.addTab(spec);

	    /*
	     * Initialise a Map tab
	     */
	    intent = new Intent().setClass(this, MainMapsActivity.class);
	    spec = tabHost.newTabSpec("taskMap").setIndicator(getString(R.string.smap_taskMap)).setContent(intent);
	    tabHost.addTab(spec);

		// hack to set font size
		LinearLayout ll = (LinearLayout) tabHost.getChildAt(0);
		TabWidget tw = (TabWidget) ll.getChildAt(0);

		int fontsize = Collect.getQuestionFontsize();

		ViewGroup rllf = (ViewGroup) tw.getChildAt(0);
		mTVFF = getTextViewChild(rllf);
		if (mTVFF != null) {
			mTVFF.setTextSize(fontsize);
			mTVFF.setTextColor(Color.WHITE);
			mTVFF.setPadding(0, 0, 0, 6);
		}

		ViewGroup rlrf = (ViewGroup) tw.getChildAt(1);
		mTVDF = getTextViewChild(rlrf);
		if (mTVDF != null) {
			mTVDF.setTextSize(fontsize);
			mTVDF.setTextColor(Color.WHITE);
			mTVDF.setPadding(0, 0, 0, 6);
		}
	}
	
	private TextView getTextViewChild(ViewGroup viewGroup) {
		for (int i = 0; i < viewGroup.getChildCount(); i++) {
			View view = viewGroup.getChildAt(i);
			if (view instanceof TextView) {
				return (TextView) view;
			}
		}
		return null;
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_ENTERDATA, 0, R.string.enter_data).setIcon(
						android.R.drawable.ic_menu_edit),
						MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_GETTASKS, 1, R.string.smap_get_tasks).setIcon(
						android.R.drawable.ic_menu_rotate),
						MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_PREFERENCES, 2, R.string.server_preferences).setIcon(
						android.R.drawable.ic_menu_preferences),
						MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_GETFORMS, 3, R.string.get_forms).setIcon(
						android.R.drawable.ic_input_add),
						MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_SENDDATA, 4, R.string.send_data).setIcon(
						android.R.drawable.ic_menu_send),
						MenuItem.SHOW_AS_ACTION_IF_ROOM);
		
		CompatibilityUtils.setShowAsAction(
				menu.add(0, MENU_MANAGEFILES, 5, R.string.manage_files).setIcon(
						android.R.drawable.ic_delete),
				MenuItem.SHOW_AS_ACTION_IF_ROOM);
	
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ENTERDATA:
            	processEnterData();
            	return true;
            case MENU_PREFERENCES:
            	createPreferencesMenu();
                return true;
            case MENU_GETFORMS:
            	processGetForms();	
            	return true;
            case MENU_SENDDATA:
            	processSendData();
            	return true;
            case MENU_GETTASKS:
                processGetTask();	
                return true;
            case MENU_MANAGEFILES:
            	processManageFiles();
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }
    
    /*
     * Process menu options
     */
    public void createPreferencesMenu() {
        Intent i = new Intent(this, PreferencesActivity.class);
        startActivity(i);
    }
    
    private void processEnterData() {
    	Intent i = new Intent(getApplicationContext(), org.odk.collect.android.activities.FormChooserList.class);
        startActivityForResult(i, FORM_CHOOSER);
    }
    
    // Get new forms
    private void processGetForms() {   
    	
		Collect.getInstance().getActivityLogger().logAction(this, "downloadBlankForms", "click");
		Intent i = new Intent(getApplicationContext(), FormDownloadList.class);
		startActivity(i);
    }
    
    // Send data
    private void processSendData() {
    	Intent i = new Intent(getApplicationContext(), org.odk.collect.android.activities.InstanceUploaderList.class);
        startActivityForResult(i, INSTANCE_UPLOADER);
    }
    
    // Get tasks from the task management server
    private void processGetTask() {   
    	
    	mProgressMsg = getString(R.string.smap_synchronising);	
    	showDialog(PROGRESS_DIALOG);
        mDownloadTasks = new DownloadTasksTask();
        mDownloadTasks.setDownloaderListener(this, mContext);
        mDownloadTasks.execute();
    }
    
	/*
	 * Download task methods
	 */
    @Override
	public void progressUpdate(String progress) {
		mProgressMsg = progress;
		mProgressDialog.setMessage(mProgressMsg);		
	}
	
    private void processManageFiles() {
    	Intent i = new Intent(getApplicationContext(), org.odk.collect.android.activities.FileManagerTabs.class);
        startActivity(i);
    }
    
    /*
	 */
	public void taskDownloadingComplete(HashMap<String, String> result) {
		
		Log.i("taskDownloadingComplete", "Complete");
    	Log.i("++++taskDownloadingComplete", "Send intent");
    	Intent intent = new Intent("refresh");
	    LocalBroadcastManager.getInstance(getApplication()).sendBroadcast(intent);
	    
		try {
            dismissDialog(PROGRESS_DIALOG);
            removeDialog(PROGRESS_DIALOG);
        } catch (Exception e) {
            // tried to close a dialog not open. don't care.
        }
		try {
			dismissDialog(ALERT_DIALOG);
            removeDialog(ALERT_DIALOG);
        } catch (Exception e) {
            // tried to close a dialog not open. don't care.
        }

		if(result != null) {
	        StringBuilder message = new StringBuilder();
	        Set<String> keys = result.keySet();
	        Iterator<String> it = keys.iterator();
	
	        while (it.hasNext()) {
	            String key = it.next();
	            if(key.equals("err_not_enabled")) {
	            	message.append(this.getString(R.string.smap_tasks_not_enabled));
	            } else if(key.equals("err_no_tasks")) {
	            	// No tasks is fine, in fact its the most common state
	            	//message.append(this.getString(R.string.smap_no_tasks));
	            } else {	
	            	message.append(key + " - " + result.get(key) + "\n\n");
	            }
	        }
	
	        mAlertMsg = message.toString().trim();
	        if(mAlertMsg.length() > 0) {
	        	showDialog(ALERT_DIALOG);
	        } 
	        
		} 
	}
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     * Debug code used in development of new Intents
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        

    	if(resultCode == RESULT_OK) {
	        switch (requestCode) {
	            // returns with a form path, start entry
	            case 10:
	            	if (intent.hasExtra("status")) {
	            		String status = intent.getExtras().getString("status");
	            		if(status.equals("success")) {
	            			if (intent.hasExtra("instanceUri")) {
	    	            		String instanceUri = intent.getExtras().getString("instanceUri");
	    	                	Intent i = new Intent(this, org.odk.collect.android.activities.FormEntryActivity.class);
	    	                	Uri inst = Uri.parse(instanceUri); 
	    	                	i.setData(inst);
	    	                	startActivityForResult(i, 10);
	    	            	}
	            			
	            		} else {
	            			if (intent.hasExtra("message")) {
	    	            		String message = intent.getExtras().getString("message");
	    	            		Log.e("MainListActivity", message);
	            			}
	            			
	            		}
	            	}
	            	
	                break;
	            default:
	                break;
	        }
	        //super.onActivityResult(requestCode, resultCode, intent);
    	}
    	return;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                    new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mDownloadTasks.setDownloaderListener(null, mContext);
                            mDownloadTasks.cancel(true);
                            // Refresh the task list
                            Intent intent = new Intent("refresh");
                	        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                        }
                    };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mProgressMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case ALERT_DIALOG:
                mAlertDialog = new AlertDialog.Builder(this).create();
                mAlertDialog.setMessage(mAlertMsg);
                mAlertDialog.setTitle(getString(R.string.smap_get_tasks));
                DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int i) {
                    	dialog.dismiss();
                    }
                };
                mAlertDialog.setCancelable(false);
                mAlertDialog.setButton(getString(R.string.ok), quitListener);
                mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
                return mAlertDialog;
    		case PASSWORD_DIALOG:

    			AlertDialog.Builder builder = new AlertDialog.Builder(this);
    			final AlertDialog passwordDialog = builder.create();

    			passwordDialog.setTitle(getString(R.string.enter_admin_password));
    			final EditText input = new EditText(this);
    			input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
    			input.setTransformationMethod(PasswordTransformationMethod
    					.getInstance());
    			passwordDialog.setView(input, 20, 10, 20, 10);

    			passwordDialog.setButton(AlertDialog.BUTTON_POSITIVE,
    					getString(R.string.ok),
    					new DialogInterface.OnClickListener() {
    						public void onClick(DialogInterface dialog,
    								int whichButton) {
    							String value = input.getText().toString();
    							String pw = mAdminPreferences.getString(
    									AdminPreferencesActivity.KEY_ADMIN_PW, "");
    							if (pw.compareTo(value) == 0) {
    								Intent i = new Intent(getApplicationContext(),
    										AdminPreferencesActivity.class);
    								startActivity(i);
    								input.setText("");
    								passwordDialog.dismiss();
    							} else {
    								Toast.makeText(
    										MainTabsActivity.this,
    										getString(R.string.admin_password_incorrect),
    										Toast.LENGTH_SHORT).show();
    								Collect.getInstance()
    										.getActivityLogger()
    										.logAction(this, "adminPasswordDialog",
    												"PASSWORD_INCORRECT");
    							}
    						}
    					});

    			passwordDialog.setButton(AlertDialog.BUTTON_NEGATIVE,
    					getString(R.string.cancel),
    					new DialogInterface.OnClickListener() {

    						public void onClick(DialogInterface dialog, int which) {
    							Collect.getInstance()
    									.getActivityLogger()
    									.logAction(this, "adminPasswordDialog",
    											"cancel");
    							input.setText("");
    							return;
    						}
    					});

    			passwordDialog.getWindow().setSoftInputMode(
    					WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    			return passwordDialog;
        }
        return null;
    }

	@Override
	public void formsDownloadingComplete(HashMap<FormDetails, String> result) {
		// TODO Auto-generated method stub
		// Ignore formsDownloading is called synchronously from taskDownloader
	}

	@Override
	public void progressUpdate(String currentFile, int progress, int total) {
		// TODO Auto-generated method stub
		mProgressMsg = getString(R.string.fetching_file, currentFile, progress, total);
		mProgressDialog.setMessage(mProgressMsg);
	}

	@Override
	public void uploadingComplete(HashMap<String, String> result) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void progressUpdate(int progress, int total) {
		 mAlertMsg = getString(R.string.sending_items, progress, total);
	        mProgressDialog.setMessage(mAlertMsg);
	}

	@Override
	public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
		// TODO Auto-generated method stub
		
	}

}
