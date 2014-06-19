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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import org.odk.collect.android.activities.FormDownloadList;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.activities.InstanceUploaderList;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.FileDbAdapter;
import org.odk.collect.android.preferences.PreferencesActivity;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.database.TaskAssignment;
import org.smap.smapTask.android.R;
import org.smap.smapTask.android.listeners.TaskDownloaderListener;
import org.smap.smapTask.android.tasks.DownloadTasksTask;
import org.smap.smapTask.android.utilities.RouteToTaskWaiter;

import com.nutiteq.BasicMapComponent;
import com.nutiteq.android.MapView;
import com.nutiteq.cache.AndroidFileSystemCache;
import com.nutiteq.cache.MemoryCache;
import com.nutiteq.components.Line;
import com.nutiteq.components.LineStyle;
import com.nutiteq.components.OnMapElement;
import com.nutiteq.components.Place;
import com.nutiteq.components.PlaceIcon;
import com.nutiteq.components.PlaceLabel;
import com.nutiteq.components.Placemark;
import com.nutiteq.components.PolyStyle;
import com.nutiteq.components.Polygon;
import com.nutiteq.components.Route;
import com.nutiteq.components.RouteInstruction;
import com.nutiteq.components.WgsBoundingBox;
import com.nutiteq.components.WgsPoint;
import com.nutiteq.fs.FileSystem;
import com.nutiteq.fs.JSR75FileSystem;
import com.nutiteq.listeners.ErrorListener;
import com.nutiteq.listeners.OnMapElementListener;
import com.nutiteq.log.Log;
import com.nutiteq.maps.OpenStreetMap;
import com.nutiteq.maps.StoredMap;
import com.nutiteq.services.DirectionsService;
import com.nutiteq.services.DirectionsWaiter;
import com.nutiteq.services.YourNavigationDirections;
import com.nutiteq.ui.ThreadDrivenPanning;
import com.nutiteq.utils.Utils;
import com.nutiteq.wrappers.AppContext;
import com.nutiteq.wrappers.Graphics;
import com.nutiteq.wrappers.Image;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.Time;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.Toast;
import android.widget.ZoomControls;

/**
 * Responsible for displaying maps of tasks.
 * 
 * @author Neil Penman 
 */
public class MainMapsActivity extends Activity implements TaskDownloaderListener, OnMapElementListener  {
	
	MapView mMapView;
    int mSelectedOverlayItem;
    FileDbAdapter fda = null;
	Cursor mTaskListCursor = null;
    public DownloadTasksTask mDownloadTasks;
	private boolean onRetainCalled;
	private static Image[] icons = {
        Utils.createImage("/res/drawable/user_marker.png"),
        Utils.createImage("/res/drawable/turn1.png"),
        Utils.createImage("/res/drawable/turn2.png"),
        Utils.createImage("/res/drawable/turn3.gif")};
	
    // menu options
    private static final int MENU_PREFERENCES = Menu.FIRST;
    private static final int MENU_ENTERDATA = Menu.FIRST + 1;
    private static final int MENU_SENDDATA = Menu.FIRST + 3;
    private static final int MENU_ZOOMTODATA = Menu.FIRST + 4;
    private static final int MENU_GETFORMS = Menu.FIRST + 6;
	
    // request codes for returning chosen form to main menu.
    private static final int FORM_CHOOSER = 0;
    private static final int INSTANCE_CHOOSER = 1;
    private static final int INSTANCE_UPLOADER = 2;
    private static final int REQUEST_AUTHENTICATE = 3;
    
    private String mProgressMsg;
    private static final int PROGRESS_DIALOG = 1;
    private static final int PROGRESS_ROUTING = 2;
	private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;
	private Context mContext;
	private Cursor taskListCursor;
	
	private LocationManager locationManager;
	private LocationListener locationListener;
	private BasicMapComponent mapComponent;
	public static RouteToTaskWaiter instance;
	private Bitmap icon;
	private Bitmap taskDoneIcon;
	private Placemark pMark;
	private Placemark pMarkDone;
	private boolean routingInprogress = false;
	private boolean routingEnabled = false;			// TODO add menu option to enable routing
	private Place userPlace = null;
	private Place currentPlace = null;
	private OnMapElement[] taskPlaces;				// Store the tasks as locations on the map in here
	private Polygon[] polyArray;
	private Line[] lineArray;
	private ArrayList<Line> routeLines = new ArrayList<Line>();
	private boolean justCreated = true;
    private StoredMap storedMap;
	
	private static final PolyStyle POLY_ACCEPTED =  new PolyStyle(Color.BLUE & 0x20ffffff, PolyStyle.FILL, new LineStyle(Color.BLUE,3));
	private static final PolyStyle POLY_DONE =  new PolyStyle(Color.GREEN & 0x20ffffff, PolyStyle.FILL, new LineStyle(Color.GREEN,3));
	private static final PolyStyle POLY_MISSED =  new PolyStyle(Color.RED & 0x20ffffff, PolyStyle.FILL, new LineStyle(Color.RED,3));
	private static final PolyStyle POLY_PENDING =  new PolyStyle(Color.YELLOW & 0x20ffffff, PolyStyle.FILL, new LineStyle(Color.YELLOW,3));
	private static final LineStyle LINE_ACCEPTED = new LineStyle(Color.BLUE,3);
	private static final LineStyle LINE_DONE = new LineStyle(Color.GREEN,3);
	private static final LineStyle LINE_PENDING = new LineStyle(Color.YELLOW,3);
	private static final LineStyle LINE_MISSED = new LineStyle(Color.RED,3);
	
	
	
	// handler for received Intents for the "refresh" event 
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		  @Override
		  public void onReceive(Context context, Intent intent) {
			  refreshTaskOverlay();
		  }
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_map);
        onRetainCalled = false;
        mapComponent = new BasicMapComponent("ef0d3930a7b6c95bd2b32ed45989c61f507eb65ee43283.04602330", 
        		new AppContext(this), 1, 1, new WgsPoint(144.959, -37.818), 10);
        mapComponent.setMap(OpenStreetMap.MAPNIK);
        
        final MemoryCache memoryCache = new MemoryCache(10*1024*1024);
        final File cacheDir = new File("/sdcard/maps_lib_cache");
        if (!cacheDir.exists()) {
          cacheDir.mkdir();
        }
        final AndroidFileSystemCache fileSystemCache = new AndroidFileSystemCache(this, "network_cache", cacheDir, 10*1024 * 1024);
//        mapComponent.setNetworkCache(new CachingChain(new Cache[] { memoryCache, fileSystemCache }));
        mapComponent.setNetworkCache(fileSystemCache);
       
        storedMap = new StoredMap("x", "/sdcard/maps_lib_cache", true);
        final Image missing = Image.createImage(storedMap.getTileSize(), storedMap.getTileSize());
        final Graphics graphics = missing.getGraphics();
        graphics.setColor(0xFFCCCECC);
        graphics.fillRect(0, 0, storedMap.getTileSize(), storedMap.getTileSize());
        storedMap.setMissingTileImage(missing);
        
        FileSystem fs = new JSR75FileSystem();
        mapComponent.setFileSystem(fs);
        mapComponent.setErrorListener(new MyErrorListener());
        
        mapComponent.setPanningStrategy(new ThreadDrivenPanning());
        mapComponent.startMapping();
        mapComponent.looseFocusOnDrag(true);
		
        // Define the location marker
     	icon = BitmapFactory.decodeResource(getResources(), R.drawable.user_marker);
     	pMark = new PlaceIcon(Image.createImage(icon), icon.getWidth()/2, icon.getHeight());
     	
        // Define the completed task actual location marker
     	taskDoneIcon = BitmapFactory.decodeResource(getResources(), R.drawable.task_done_marker);
     	pMarkDone = new PlaceIcon(Image.createImage(taskDoneIcon), icon.getWidth()/2, icon.getHeight());
     	
        // get the mapview that was defined in main.xml
        mMapView = (MapView)findViewById(R.id.mapview);
        //registerForContextMenu(mMapView);
       

        // mapview requires a mapcomponent
        mMapView.setMapComponent(mapComponent);
        

        
		mapComponent.setOnMapElementListener(this);
		mMapView.setFocusable(true);
		
		
        ZoomControls zoomControls = (ZoomControls)findViewById(R.id.zoomcontrols);
        
		// set zoomcontrols listeners to enable zooming
		zoomControls.setOnZoomInClickListener(new View.OnClickListener() {
			public void onClick(final View v) {
				mapComponent.zoomIn();
			}
		});
		
		zoomControls.setOnZoomOutClickListener(new View.OnClickListener() {
			public void onClick(final View v) {
				mapComponent.zoomOut();
			}
		});

		/*
		 * Add a location listener
		 */
		locationManager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
		locationListener = new LocationListener() {
			
			@Override
			public void onLocationChanged(Location location) {
				System.out.println("onLocationChanged() " + location.getLongitude() + ":" + location.getLatitude());
				
				if (userPlace != null) {
					mapComponent.removePlace(userPlace);
				}
				
				// TODO check for accuracy, save previous location
				WgsPoint point = new WgsPoint(location.getLongitude(), location.getLatitude());
				userPlace = new Place(0, new PlaceLabel("Location"), pMark, point);				
				mapComponent.addPlace(userPlace);
			}

			@Override
			public void onProviderDisabled(String arg0) {
				
			}

			@Override
			public void onProviderEnabled(String provider) {
				
			}

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {
				
			}		
			
		};
		
		
		mapComponent.setMiddlePoint(new WgsPoint(144.95987, -37.81819));
    	mapComponent.setSmoothZoom(true);

    }
    
    class MyErrorListener implements ErrorListener
    {
    	public void licenseError(String message)
    	{
    		mapComponent.setMap(storedMap);
    		System.out.println( "Nutiteq License Error!! Map is disabled...");
    	}
    	public void networkError(String message)
    	{
    		mapComponent.setMap(storedMap);
    		System.out.println("Network Error!! Map is disabled...");
    	}
    }
    @Override
    protected void onPause() {
        super.onPause();
    	// Unregister since the activity is not visible
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    	locationManager.removeUpdates(locationListener);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("onResume()");
		if ( locationManager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {		
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, (float) 10.0, locationListener);
		} 
		//if ( locationManager.isProviderEnabled( LocationManager.NETWORK_PROVIDER ) ) {		
		//	locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 10, locationListener);
		//}
		
    	// Register mMessageReceiver to receive messages. (from http://www.vogella.com/articles/AndroidBroadcastReceiver/article.html)
    	LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
    	      new IntentFilter("refresh"));
    	
        refreshTaskOverlay();

    }
    
    @Override 
    public Object onRetainNonConfigurationInstance() { 
    	onRetainCalled = true; 
    	return mapComponent; 
	}
    
    @Override 
    protected void onDestroy() { 
    	super.onDestroy();
    	
    	if (!onRetainCalled) {
    		mapComponent.stopMapping();
    		mapComponent = null; 
		}
	}

    
    private void calculateNewRoute() {
    	
    	WgsPoint userLocation = userPlace.getWgs();
    	WgsPoint endCoordinates = currentPlace.getWgs();
		if (endCoordinates != null &&
				endCoordinates.getLon() != userLocation.getLon() &&
				endCoordinates.getLat() != userLocation.getLat()) {
			// TODO close the old routing thread ?
			Thread routingThread = new Thread(new RoutingThread(new RoutingHandler(), RoutingHandler.ROUTING_YOURNAVIGATION));
			
			routingThread.start();
			routingInprogress = true;
		}
    }
    

    


    /*
     * The following options menu functions are replicated from MainListActivity - they should be shared!
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_ZOOMTODATA, 5, getString(R.string.smap_zoom_data));

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ZOOMTODATA:
        	setMaxBoundingBox();
        	return true;
    }
    return super.onOptionsItemSelected(item);
    }
    
    private void processSyncTask() {
    	mProgressMsg = getString(R.string.smap_synchronising);	
    	showDialog(PROGRESS_DIALOG);
        mDownloadTasks = new DownloadTasksTask();
        mDownloadTasks.setDownloaderListener(this, mContext);
        mDownloadTasks.execute();
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
                            refreshTaskOverlay();
                        }
                    };
                mProgressDialog.setTitle(getString(R.string.downloading_data));
                mProgressDialog.setMessage(mProgressMsg);
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return mProgressDialog;
            case PROGRESS_ROUTING:
                mProgressDialog = new ProgressDialog(this);
                mProgressDialog.setTitle(getString(R.string.smap_finding_route));
                mProgressDialog.setMessage(getString(R.string.smap_route_finding_inprogress));
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                return mProgressDialog;
        }
        return null;
    }
    
	/*
	 * (non-Javadoc)
	 * @see org.smap.smapTask.android.listeners.TaskDownloaderListener#progressUpdate(int)
	 */
	public void progressUpdate(String progress) {
		mProgressMsg = progress;
		mProgressDialog.setMessage(mProgressMsg);		
	}
	
    /*
     * (non-Javadoc)
     * @see org.smap.smapTask.android.listeners.TaskDownloaderListener#taskDownloadingComplete()
     */
	public void taskDownloadingComplete(HashMap<String, String> result) {
        dismissDialog(PROGRESS_DIALOG);
    
        refreshTaskOverlay();
	}
	
    /**
     * Upon return, check intent for data needed to launch other activities.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_CANCELED) {
            return;
        }

        String formPath = null;
        Intent i = null;
        switch (requestCode) {
            // returns with a form path, start entry
            case FORM_CHOOSER:               
            	formChooser(intent, null);
                break;
            // returns with an instance path, start entry
            case INSTANCE_CHOOSER:
                formPath = intent.getStringExtra(FormEntryActivity.KEY_FORMPATH);
                String instancePath = intent.getStringExtra(FormEntryActivity.KEY_INSTANCEPATH);
                i = new Intent("org.smap.smapTask.android.action.FormEntry");
                i.putExtra(FormEntryActivity.KEY_FORMPATH, formPath);
                i.putExtra(FormEntryActivity.KEY_INSTANCEPATH, instancePath);
                startActivity(i);
                break;
            case REQUEST_AUTHENTICATE:	// TODO what if this returns use not permitted?
            	processSyncTask();
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }
   
    /*
     * Add the current tasks to the map
     */
    private void refreshTaskOverlay() {
    	
    	System.out.println("refreshTaskOverlay()");
        Vector<OnMapElement> places = new Vector<OnMapElement>();
        Vector<OnMapElement> polygons = new Vector<OnMapElement>();
        Vector<OnMapElement> linestrings = new Vector<OnMapElement>();
        PolyStyle polyStyle = null;
        LineStyle lineStyle = null;
       	mapComponent.removeOnMapElements(taskPlaces);		// Clear the existing tasks
       	mapComponent.removeOnMapElements(polyArray);		// Clear the existing tasks
       	mapComponent.removeOnMapElements(lineArray);		// Clear the existing tasks
       	for(Line line : routeLines) {
			mapComponent.removeLine(line);
		}
		routeLines.clear();
       	
    	
    	// get all tasks
        try {
            FileDbAdapter fda = new FileDbAdapter();
            fda.open();
        	taskListCursor = fda.fetchTasksForSource(getSource(), true);
        	fda.close();
        } catch (Exception e) {
        	e.printStackTrace(); 	// TODO handle exception
        }
        
        if (taskListCursor.moveToFirst()) {
	    	
        	do
        	{
        		int taskIdIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ID);
		        int taskLonIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_LON);
		    	int taskLatIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_LAT);
		    	int taskNameIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_TITLE);
		    	int taskStatusIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_STATUS);
		    	int taskALonIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ADHOC_LON);
		    	int taskALatIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_ADHOC_LAT);
		    	int taskLocationIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_LOCATION);
		    	int taskGeomTypeIndex = taskListCursor.getColumnIndex(FileDbAdapter.KEY_T_GEOM_TYPE);
		    	
		    	int taskId = taskListCursor.getInt(taskIdIndex);
		    	String taskLongitude = taskListCursor.getString(taskLonIndex);
		    	String taskLatitude = taskListCursor.getString(taskLatIndex);
		    	String taskALongitude = taskListCursor.getString(taskALonIndex);
		    	String taskALatitude = taskListCursor.getString(taskALatIndex);
		    	String taskName = taskListCursor.getString(taskNameIndex);
		    	String taskStatus = taskListCursor.getString(taskStatusIndex);
		    	String taskLocation = taskListCursor.getString(taskLocationIndex);
		    	String taskGeomType = taskListCursor.getString(taskGeomTypeIndex);

		    	if (taskLongitude == null || taskLatitude == null) {
		    		continue;
		    	}
		    	
		    	try
		    	{
		    		System.out.println("Longitude:" + taskLongitude);
		    		System.out.println("Longitude:" + taskLatitude);
		    		double lon = Double.parseDouble(taskLongitude);
		    		double lat = Double.parseDouble(taskLatitude);
		    		
	        		Bitmap icon = null;
	        		if (taskStatus.equals(FileDbAdapter.STATUS_T_ACCEPTED)) {
	        			icon = BitmapFactory.decodeResource(getResources(), R.drawable.task_marker_accepted);
	        			polyStyle = POLY_ACCEPTED;
	        			lineStyle = LINE_ACCEPTED;
	        		} else if (taskStatus.equals(FileDbAdapter.STATUS_T_DONE)) {
	        			icon = BitmapFactory.decodeResource(getResources(), R.drawable.task_marker_done);
	        			polyStyle = POLY_DONE;
	        			lineStyle = LINE_DONE;
	        		} else if (taskStatus.equals(FileDbAdapter.STATUS_T_MISSED) || 
	        				taskStatus.equals(FileDbAdapter.STATUS_T_CANCELLED) ||
	        				taskStatus.equals(FileDbAdapter.STATUS_T_REJECTED) ||
	        				taskStatus.equals(FileDbAdapter.STATUS_T_DELETED)) {
	        			icon = BitmapFactory.decodeResource(getResources(), R.drawable.task_marker_missed);
	        			polyStyle = POLY_MISSED;
	        			lineStyle = LINE_MISSED;
	        		} else if (taskStatus.equals(FileDbAdapter.STATUS_T_PENDING)) {
	        			icon = BitmapFactory.decodeResource(getResources(), R.drawable.task_marker_pending);
	        			polyStyle = POLY_PENDING;
	        			lineStyle = LINE_PENDING;
	        		}
	        		
	        		if(icon != null) {  // Ignore if an icon was not found for the status
		                final Placemark defaultIcon = new PlaceIcon(Image.createImage(icon), icon.getWidth()/2, icon.getHeight());
		                
		                // Add an extra marker to show actual location of completed task
		                if (taskStatus.equals(FileDbAdapter.STATUS_T_DONE)) {
		                	try {
		                		if(taskALongitude != null && taskALatitude != null) {
		                			double lonA = Double.parseDouble(taskALongitude);
		                			double latA = Double.parseDouble(taskALatitude);
		        			    	WgsPoint donePoint = new WgsPoint(lonA, latA);
		        			    	Place donePlace = new Place((int) taskId, new PlaceLabel(taskName), pMarkDone, donePoint);
		        			    	places.add(donePlace);
		                		}
		                	} catch (Exception e) {
		                		e.printStackTrace();
		                	}
				    	}
		                
		                System.out.println("refreshTaskOverlay: task status:" + taskStatus + " -- " + lon + ":" + lat);
				    	WgsPoint taskPoint = new WgsPoint(lon, lat);
				    	System.out.println("Adding task:" + taskId);
				    	
				    	Place newPlace = new Place((int) taskId, new PlaceLabel(taskName), defaultIcon, taskPoint);
				    	if(taskGeomType != null && !taskGeomType.equals("POINT")) {
				    		System.out.println("location:" + taskLocation);
				    		String[] coords = taskLocation.split(",");
				    		if(coords.length > 1) {
				    			Vector<WgsPoint> points = new Vector<WgsPoint>();
				    			for (String c : coords) {
				    				String [] cElem = c.split(" ");
				    				if(cElem.length == 2) {
				    					WgsPoint p = new WgsPoint(Double.parseDouble(cElem[0]), Double.parseDouble(cElem[1]));
				    					points.add(p);
				    				}
				    			}
				    			WgsPoint[] pa = new WgsPoint[points.size()];
				    	        points.copyInto(pa);
				    	        
				    			if(taskGeomType.equals("POLYGON")) {    				
				    				Polygon aPolygon = new Polygon(pa, polyStyle);
				    				polygons.add(aPolygon);
					    		} else if (taskGeomType.equals("LINESTRING")) {
					    			Line aLine = new Line(pa, lineStyle);
				    				linestrings.add(aLine);
					    		}
				    		}
				    		
				    	}
				    	places.add(newPlace);
	        		}
			    	
		    	} catch (NumberFormatException numberFormatException) {
		    		numberFormatException.printStackTrace();
		    	}
		    	
        	} while (taskListCursor.moveToNext());       	
        }
        
        taskListCursor.close();
        
        // Save a copy of the task places so that they can be removed easily
        taskPlaces = new OnMapElement[places.size()];
        places.copyInto(taskPlaces);
        mapComponent.addOnMapElements(taskPlaces);
        
        if(polygons.size() > 0) {
        	polyArray = new Polygon[polygons.size()];
        	polygons.copyInto(polyArray);
        	mapComponent.addOnMapElements(polyArray);
        }
        if(linestrings.size() > 0) {
        	lineArray = new Line[linestrings.size()];
        	linestrings.copyInto(lineArray);
        	mapComponent.addOnMapElements(lineArray);
        }

		if(!justCreated) {	// Prevent nutiteq zooming to to whole world first time onResume is called
			setMaxBoundingBox();
		}
		justCreated = false;
		
    }
    
    /*
     * Sets the bounding box to the maximum of the current tasks and the current location
     */
    private boolean setMaxBoundingBox() {

    	WgsBoundingBox bbox = new WgsBoundingBox(180.0, 90.0, -180.0, -90.0);
    	boolean setBbox = false;
    	
    	// Add the tasks
    	int count = 0;
    	for(int i = 0; i < taskPlaces.length; i++) {
    		bbox = addToBoundingBox(bbox, ((Place) taskPlaces[i]).getWgs());
    		count++;
    	}
		// Add the current location to boundary (only if routing is enabled)
		if(routingEnabled && userPlace != null) {
			bbox = addToBoundingBox(bbox, userPlace.getWgs());
			count++;
		}
		
		if(count > 0) {
			// Make sure we do have a box
			bbox = nudgeBoxSize(bbox);
			mapComponent.loosePlaceFocus();
			mapComponent.setBoundingBox(bbox);	
			setBbox = true;
		}
		return setBbox;

    }
    
    // Add a point to a bounding box
    private WgsBoundingBox addToBoundingBox(WgsBoundingBox bbox, WgsPoint point) {
		double lon = point.getLon();
		double lat = point.getLat();
		
		double maxLon = bbox.getWgsMax().getLon();
		double maxLat = bbox.getWgsMax().getLat();
		double minLon = bbox.getWgsMin().getLon();
		double minLat = bbox.getWgsMin().getLat();
		
		if (lon > maxLon) {
			maxLon = lon;
		}
		
		if (lon < minLon) {
			minLon = lon;
		}
		
		if (lat > maxLat) {
			maxLat = lat;
		}
		
		if (lat < minLat) {
			minLat = lat;
		}
		return new WgsBoundingBox(minLon, minLat, maxLon, maxLat);
    }
    
    /*
     * 1) Ensure the box is actually a box and not a point or a line.
     * 2) Add a 10% margin to the box
     */
    private WgsBoundingBox nudgeBoxSize(WgsBoundingBox bbox) {
		double maxLon = bbox.getWgsMax().getLon();
		double maxLat = bbox.getWgsMax().getLat();
		double minLon = bbox.getWgsMin().getLon();
		double minLat = bbox.getWgsMin().getLat();
		
		if(maxLon == minLon) {
			maxLon += 0.1;
			minLon -= 0.1; 
		}
		if(maxLat == minLat) {
			maxLat += 0.1;
			minLat -= 0.1; 
		}
		maxLat = maxLat + (maxLat - minLat) * 0.1;
		minLat = minLat - (maxLat - minLat) * 0.1;
		maxLon = maxLon + (maxLon - minLon) * 0.1;
		minLon = minLon - (maxLon - minLon) * 0.1;
		
		return new WgsBoundingBox(minLon, minLat, maxLon, maxLat);
    }
    
    private void formChooser(Intent intent, String instancePath) {
        String formPath = intent.getStringExtra(FormEntryActivity.KEY_FORMPATH);
        Intent i = null;
        
        // Create the local task and get the task id as a string to pass to the form entry activity
        Time t = new Time();
        t.setToNow();
        long tid = -1;
        try {
        	TaskAssignment ta = new TaskAssignment();
        	/*
        	 * TODO Implement
        	task.scheduledStart = t.toMillis(false);
        	task.status = "open";
        	task.taskForm = STFileUtils.getFileName(formPath);
        	tid = mTda.createTask(-1, "local", null, task);
        	*/
        } catch (Exception e) {
        	e.printStackTrace();		// TODO handle exception
        }          	 
        
        i = new Intent("org.smap.smapTask.android.action.FormEntry");
        i.putExtra(FormEntryActivity.KEY_FORMPATH, formPath);
        i.putExtra(FormEntryActivity.KEY_TASK, tid);
        if(instancePath != null) {
        	i.putExtra(FormEntryActivity.KEY_INSTANCEPATH, instancePath);
        }
        startActivity(i);
    }
	
	@Override
	public void elementLeft(OnMapElement element) {
		currentPlace = null;
	}
	
	@Override
	public void elementEntered(OnMapElement element) {
		
		if (element != null && element instanceof Place) {
			currentPlace = (Place) element;
			
			if(routingEnabled) {
				placeSelected(element);
			} else {
				openContextMenu(mMapView);	// No routing lets just look at the menu
			}
		}
	}
	
	@Override
	public void elementClicked(OnMapElement element) {

		if (element != null && element instanceof Place) {
			System.out.println("elementclicked opening context");
			currentPlace = (Place) element;
			//openContextMenu(mMapView);
			longClickOnMap(mMapView);
		}
	}
	
	/*
	 * Calculate the route to the selected place 
	 */
	private void placeSelected(OnMapElement element) {

		if (element != null && element instanceof Place) {
			
			// Calculate a new route to this assignment
			if(!routingInprogress && userPlace != null) {
				calculateNewRoute();
			}
			
			// Center map
			//mapComponent.loosePlaceFocus();
			//if(startCoordinates != null) {
			//	System.out.println("Setting bounding box");
			//	mapComponent.setBoundingBox(new WgsBoundingBox(startCoordinates, endCoordinates));
			//} else {
			//	mapComponent.focusOnPlace(selectedPlace, true);
			//}
		}
	}
	
	class RoutingHandler implements DirectionsWaiter {

		public static final int ROUTING_CLOUDMADE = 1;
		public static final int ROUTING_YOURNAVIGATION = 2;
		
		private void clearLines() {
			for(Line line : routeLines) {
				mapComponent.removeLine(line);
			}
			routeLines.clear();
		}
		
		private void addLine(Line line) {
			WgsPoint[] points = line.getPoints();
			System.out.println("Adding line with " + points.length + " points");
			//for(int i = 0; i < points.length; i++) {
			//	System.out.println("    route point:" + points[i].getLon() + " : " + points[i].getLat());
			//}
			System.out.println("Adding line");
			mapComponent.addLine(line);
			routeLines.add(line);
		}
		
		@Override
		public void networkError() {
			Log.error("networkError:" + getString(R.string.smap_routing_network_error));
			routingInprogress = false;			
		}

		@Override
		public void routeFound(Route route) {
	        System.out.println("routeFound: " + getString(R.string.smap_found_route));
	                
	        clearLines();
	        // add route as line to map
	        Line routeLine = route.getRouteLine();
	        int[] lineColors = {0xFF0000FF, 0xFF00FF00}; // 0 - blue, 1 - green
	        
	        routeLine.setStyle(new LineStyle(lineColors[0],4)); // set non-default style for the route

	        addLine(routeLine);
	        System.out.println("routeFound: total distance="+route.getRouteSummary().getDistance().getValue() + " " + route.getRouteSummary().getDistance().getUnitOfMeasure() + "s");

	        final RouteInstruction[] instructions = route.getInstructions();
	        if(instructions.length > 0) {
	        	System.out.println("Instructions:" + instructions.length);
				Place[] instructionPlaces = new Place[instructions.length];
				
				for (int i = 0; i < instructions.length; i++) {
				  instructionPlaces[i] = new Place(i, instructions[i].getInstruction(), icons[instructions[i].getInstructionType()], instructions[i]
				      .getPoint());
				}
						
				mapComponent.addPlaces(instructionPlaces);
				//if(instructionPlaces[0] != null){
				//	mapComponent.setMiddlePoint(instructionPlaces[0].getWgs()); 
				//}
	        }
			routingInprogress = false;			
		}

		@Override
		public void routingErrors(int errorCodes) {
			final StringBuffer errors = new StringBuffer("Errors: ");
	        if ((errorCodes & DirectionsService.ERROR_DESTINATION_ADDRESS_NOT_FOUND) != 0) {
	            errors.append(getString(R.string.smap_destination_not_found));
	          }
	          if ((errorCodes & DirectionsService.ERROR_FROM_ADDRESS_NOT_FOUND) != 0) {
	            errors.append(getString(R.string.smap_destination_not_found));
	          }
	          if ((errorCodes & DirectionsService.ERROR_FROM_AND_DESTINATION_ADDRESS_SAME) != 0) {
	            errors.append(getString(R.string.smap_destination_not_found));
	          }
	          if ((errorCodes & DirectionsService.ERROR_ROUTE_NOT_FOUND) != 0) {
	            errors.append(getString(R.string.smap_destination_not_found));
	          }
	          Log.error("routingErrors:" + errors.toString());
	  		routingInprogress = false;
			
		}

		@Override
		public void routingParsingError(String arg0) {
			Log.error("routingParsingError:" + getString(R.string.smap_route_parse_error) + " - " + arg0);
			routingInprogress = false;
		}
	
	}

	class RoutingThread implements Runnable {
        private RoutingHandler routingListener;
        private int routingService;

        public RoutingThread(RoutingHandler routingListener, int routingService) {
            this.routingListener = routingListener;
            this.routingService = routingService;
        }

        public void run() {
        	if(currentPlace != null) {
	            switch(routingService){
	                case RoutingHandler.ROUTING_CLOUDMADE:
	                    //new CloudMadeDirections(nutiteqRouteWaiter,nutiteqRouteWaiter.getStartCoordinates(), nutiteqRouteWaiter.getEndCoordinates(), "Foot",CloudMadeDirections.ROUTE_TYPE_MODIFIER_SHORTEST ,"222c0ceb31794934a888ed9403a005d8",userId).execute();
	                    break;
	                case RoutingHandler.ROUTING_YOURNAVIGATION:
	                	System.out.println("Route:" + userPlace.getWgs().toString() + "::::::" + currentPlace.getWgs().toString());
	                    new YourNavigationDirections(routingListener, userPlace.getWgs(),  currentPlace.getWgs(), YourNavigationDirections.MOVE_METHOD_CAR, YourNavigationDirections.ROUTE_TYPE_FASTEST).execute();
	                    break;
	                    
	            }
        	}
        }
	}
	
	/*
     * Handle a long click on the map
     */
    protected boolean longClickOnMap(View v) {

    	Intent i = new Intent(getApplicationContext(), org.smap.smapTask.android.activities.TaskAddressActivity.class);
        long taskId = currentPlace.getId();
    	i.putExtra("id", taskId);
		if(userPlace != null) {
			WgsPoint userLocation = userPlace.getWgs();
			i.putExtra("lon", String.valueOf(userLocation.getLon()));
        	i.putExtra("lat", String.valueOf(userLocation.getLat()));
		}
		
    	startActivity(i);
        return true;
    }
	
	/*
	 * ====================================================================================
	 * Functions that should be shared with MainListActivity
	 * Copy and paste from MainListActivity any tweaking should be noted in the comments before
	 *  each function.
	 */
	
	/*
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 * Changes:
	 *    1) Task id is obtained from current element instead of context menu
	 */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	
		System.out.println("onCreateContextMenu()");
	    if(currentPlace != null) {
	    	int taskId = currentPlace.getId();	// Use currentPlace to get task Id
	    	System.out.println("Current taskId:" + taskId);
	    	Cursor c = null;
	    	fda = new FileDbAdapter();
	    	try {
	    		fda.open();
	    		c = fda.fetchTaskForId((long) taskId);
	    		String taskStatus = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS));
	    		String taskTitle = c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_TITLE));
	    		menu.setHeaderTitle(taskTitle);
	    		if(fda.canReject(taskStatus)) {
	    			menu.add(0,R.id.reject,0,R.string.smap_reject_task);
	    		}
	    		if(fda.canComplete(c.getString(c.getColumnIndex(FileDbAdapter.KEY_T_STATUS)))) {
	    			menu.add(0,R.id.complete_task,0,R.string.smap_complete_task);
	    		}
	    		if(fda.canAccept(taskStatus)) {
	    			menu.add(0,R.id.accept_task,0,R.string.smap_accept_task);
	    		}
	    		menu.add(0,R.id.cancel_task,0, R.string.cancel);
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
    	} else {
    		System.out.println("currentPlace null");
    	}
    }
	
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
     * Changes:
     *   1) refreshList() replaced by refreshTaskOverlay()
     *   2) Task id is obtained from current element instead of context menu
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
      
    	int taskId = currentPlace.getId();	// Use currentPlace to get task Id
	  	Cursor c = null;
    	switch (item.getItemId()) {
    	
	      	case R.id.reject:
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
	        	    refreshTaskOverlay();
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
	      		return true;
	      case R.id.complete_task:	    	  

	    		try {   				

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
		    	
		    	return true;
	      case R.id.accept_task:
	    	 
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
	        	    refreshTaskOverlay();
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
	
		    	
		    	return true;
	      default:
	        return super.onContextItemSelected(item);
      }
    }   
    
	public void completeTask(String instancePath, String formPath, long taskId) {
		
		// set the adhoc location
		try {
			fda = new FileDbAdapter();
			fda.open();
			if(userPlace != null) {
				WgsPoint userLocation = userPlace.getWgs();
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
			System.out.println("MainListActivity:completeTask: Unique instance not found: count is:" + 
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