package org.smap.smapTask.android.utilities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.DownloadFormsTask;
import org.odk.collect.android.tasks.DownloadFormsTask.FileResult;
import org.odk.collect.android.utilities.FileUtils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class ManageForm {
	
	/*
	 * Parameters
	 *   formId:  	Stored as jrFormId in the forms database.
	 *   			Extracted by odk from the id attribute on top level data element in the downloaded xml
	 *   formURL:	URL to download the form
	 *   			Not stored
	 *   instanceDataURL:
	 *   			URL to download the data.
	 *   			Not stored
	 *   
	 *   Because odk uniquely identifies forms based on the id extracted from the down loaded xml file
	 *    the formId must be sourced from the task management system along with the URL so we can check
	 *    if the form has already been downloaded.  
	 */
    public ManageFormResponse insertForm(String formId, int formVersion, String formURL, String initialDataURL, String taskId) {
        String formName = null;
        String formPath = null;
        String submissionUri = null;
        String instancePath = null;
        String formVersionString = String.valueOf(formVersion);	
        
        ManageFormResponse mfResponse = new ManageFormResponse();
        
        Log.i("ManageForm: called:",  
       		 "formId:" + formId + ":" + formVersion + "-- formUrl:" + formURL + "-- initialDataUrl:" + initialDataURL);
        
		// Get the form details
   	 	Cursor c = null;		 
        try {
        	String selectionClause = FormsColumns.JR_FORM_ID + "=? AND "
					+ FormsColumns.JR_VERSION + "=?";
        	
        	String [] selectionArgs = new String[] { formId, formVersionString };
        	//String [] selectionArgs = new String [1];
        	//selectionArgs[0] = formId;
        	String [] proj = {FormsColumns._ID, FormsColumns.DISPLAY_NAME, FormsColumns.JR_FORM_ID,
        			FormsColumns.SUBMISSION_URI,FormsColumns.FORM_FILE_PATH}; 
        	
        	final ContentResolver resolver = Collect.getInstance().getContentResolver();
        	c = resolver.query(FormsColumns.CONTENT_URI, proj, selectionClause, selectionArgs, null);
            
        	if(c.getCount() > 0) {
        		
            	// Form is already on the phone
	        	 c.moveToFirst();
	        	 String table_id = c.getString(c.getColumnIndex(FormsColumns._ID));
	             formName = c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME));
	             submissionUri = c.getString(c.getColumnIndex(FormsColumns.SUBMISSION_URI));
	             formPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));

	             Log.i("ManageForm: existing form found:", table_id +  
	            		 "--" + formId + "--" + formName + "--" + 
	            		 submissionUri + "--" + formPath);
             
        	} else {
        		
            	 // Form was not found try downloading it
            	 FileResult dl = null;
            	 
            	 try {
            		 Log.i("ManageForm", "Downloading form");
            		 DownloadFormsTask dft = new DownloadFormsTask();
            		 dl = dft.downloadXform(formId, formURL);
            	 } catch (Exception e) {
            		 mfResponse.isError = true;
            		 mfResponse.statusMsg = "Unable to download form from " + formURL;
            		 return mfResponse;
            	 }
            	 

            	 try {
            		 Log.i("ManageForm", "Inserting new form into forms database");
            		 ContentValues v = new ContentValues();
            		 v.put(FormsColumns.FORM_FILE_PATH, dl.getFile().getAbsolutePath());
            		 Log.i("ManageForm abs path", dl.getFile().getAbsolutePath());
	
	                 HashMap<String, String> formInfo = FileUtils.parseXML(dl.getFile());
	                 v.put(FormsColumns.DISPLAY_NAME, formInfo.get(FileUtils.TITLE));
	                 v.put(FormsColumns.JR_VERSION, formInfo.get(FileUtils.VERSION));
	                 v.put(FormsColumns.JR_FORM_ID, formInfo.get(FileUtils.FORMID));
	                 v.put(FormsColumns.SUBMISSION_URI, formInfo.get(FileUtils.SUBMISSIONURI));
	                 v.put(FormsColumns.BASE64_RSA_PUBLIC_KEY, formInfo.get(FileUtils.BASE64_RSA_PUBLIC_KEY));
            		 Log.i("ManageForm display name", formInfo.get(FileUtils.TITLE));
            		 Log.i("ManageForm ident", formInfo.get(FileUtils.FORMID));
            		 Log.i("ManageForm version", formInfo.get(FileUtils.VERSION));
            		 
	                 formName = formInfo.get(FileUtils.TITLE);
	                 submissionUri = formInfo.get(FileUtils.SUBMISSIONURI);
	                 formPath = dl.getFile().getAbsolutePath();
	                 formId = formInfo.get(FileUtils.FORMID);	// Update the formID with the actual value in the form (should be the same)
	                 
	                 /*
	                  * Before inserting make sure this form isn't already in the database
	                  *  Although we have tested the database for the formId previously, it is
	                  *  possible that the form URL was actually pointing to a form with a different id
	                  */
	                //selectionClause = FormsColumns.JR_FORM_ID + " = ?";
	             	//selectionArgs = new String [1];
	             	//selectionArgs[0] = formId;
	             	//if(c != null) {
	             	//	c.close();
	             	//}
	                //c = resolver.query(FormsColumns.CONTENT_URI, proj, selectionClause, selectionArgs, null);
	                //if(c.getCount() == 0) {
	                	Log.i("ManageForm", "Form does not already exist, hence inserting now");
	                	Collect.getInstance().getContentResolver().insert(FormsColumns.CONTENT_URI, v);
	                //} else {
	                //	Log.i("ManageForm", "Form already downloaded");
	                //}
	                 
            	 } catch (Throwable e) {
               		 mfResponse.isError = true;
               		 Log.e("ManageForm", e.getMessage());
            		 mfResponse.statusMsg = "Unable to insert form from " + formURL + " into form database.";
               		 c.close();
            		 return mfResponse;
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
         instancePath = getInstancePath(formPath, taskId);
         if(instancePath != null && initialDataURL != null) {
        	 Log.i("ManageForm Instance Path:", instancePath);
        	 File f = null;

             f = new File(instancePath);
             DownloadFormsTask dft = new DownloadFormsTask();
             try {
             dft.downloadFile(f, initialDataURL);
             } catch (Exception e) {
            	 e.printStackTrace();
         		 mfResponse.isError = true;
            	 mfResponse.statusMsg = "Unable to download initial data from " + initialDataURL + " into file: " + instancePath;
         		 return mfResponse;
             }
         }

		    
         // Write the new instance entry into the instance content provider
         try {
        	 mfResponse.mUri = writeInstanceDatabase(formId, formVersionString, formName, submissionUri, instancePath);
         } catch (Throwable e) {
        	 e.printStackTrace();
       		 mfResponse.isError = true;
    		 mfResponse.statusMsg = "Unable to insert instance " + formId + " into instance database.";
        	 return mfResponse;
         }
         
         mfResponse.isError = false;
         mfResponse.formPath = formPath;
         mfResponse.instancePath = instancePath;
         return mfResponse;
    }
    
    private Uri writeInstanceDatabase(String jrformid, String jrVersion, String formName, 
			String submissionUri, String instancePath) throws Throwable {
    
    	Log.i("InstanceCreate", "Inserting new instance into database");
    	ContentValues values = new ContentValues();
	 
    	values.put(InstanceColumns.JR_FORM_ID, jrformid);
    	values.put(InstanceColumns.JR_VERSION, jrVersion);
    	values.put(InstanceColumns.SUBMISSION_URI, submissionUri);
    	values.put(InstanceColumns.INSTANCE_FILE_PATH, instancePath);
    	values.put(InstanceColumns.DISPLAY_NAME, formName);
    	values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
    	//values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(false));
	
    	return Collect.getInstance().getContentResolver()
    			.insert(InstanceColumns.CONTENT_URI, values);
    }
    
    /*
     * Instance path is based on basepath, filename, timestamp and the task id
     * Paramters
     *  formPath:   Used to obtain the filename
     *  taskId:	    Used to guarantee uniqueness when multiple tasks for the same form are assigned
     */
    private String getInstancePath(String formPath, String taskId) {
        String instancePath = null;
        
        if(formPath != null) {
	    	String time = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
	                        .format(Calendar.getInstance().getTime());
            String file =
                formPath.substring(formPath.lastIndexOf('/') + 1, formPath.lastIndexOf('.'));
            String path = Collect.INSTANCES_PATH + "/" + file + "_" + time + "_" + taskId;
            if (FileUtils.createFolder(path)) {
                instancePath = path + "/" + file + "_" + time + ".xml";
            }
        }
            
        return instancePath;
    }

}
