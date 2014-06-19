package org.smap.smapTask.android.utilities;

import org.smap.smapTask.android.activities.MainMapsActivity;

import android.content.Context;
import android.content.Intent;

import com.nutiteq.BasicMapComponent;
import com.nutiteq.components.Line;
import com.nutiteq.components.LineStyle;
import com.nutiteq.components.Place;
import com.nutiteq.components.Route;
import com.nutiteq.components.RouteInstruction;
import com.nutiteq.components.WgsPoint;
import com.nutiteq.log.Log;
import com.nutiteq.services.DirectionsService;
import com.nutiteq.services.DirectionsWaiter;
import com.nutiteq.utils.Utils;
import com.nutiteq.wrappers.Image;

public class RouteToTaskWaiter implements DirectionsWaiter {
	public static RouteToTaskWaiter instance;
    private WgsPoint startCoordinates;
    private WgsPoint endCoordinates;
    private Context context;
    private int routingService;
    private static Image[] icons = {
        Utils.createImage("/res/drawable/gps_marker.png"),
        Utils.createImage("/res/drawable/turn1.png"),
        Utils.createImage("/res/drawable/turn2.png"),
        Utils.createImage("/res/drawable/turn3.png")};
    
    public RouteToTaskWaiter(WgsPoint userLocation, WgsPoint taskLocation, Context context) {
    	instance = this;
        this.startCoordinates = startCoordinates;
        this.endCoordinates = endCoordinates;   
        this.context = context;
        this.routingService = routingService;
    }
    
    
	@Override
	public void networkError() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void routeFound(Route route) {
		// TODO Auto-generated method stub
//		Log.info("route Found");
//        
//        // pass route to Application
//		MainMapsActivity app = ((MainMapsActivity)((AndroidMap)context).getApplication()); 
//        app.setRoute(route);
////
//        BasicMapComponent map = app.getTheMap();
//        //TODO get map from activity
//        
//        // add route as line to map
//        Line routeLine = route.getRouteLine();
//        int[] lineColors = {0xFF0000FF, 0xFF00FF00}; // 0 - blue, 1 - green
//        
//        routeLine.setStyle(new LineStyle(lineColors[routingService],2)); // set non-default style for the route
//
//        map.addLine(routeLine);
//        Log.info("total distance="+route.getRouteSummary().getDistance().toString());
//
//        final RouteInstruction[] instructions = route.getInstructions();
//		Place[] instructionPlaces = new Place[instructions.length];
//		
//		for (int i = 0; i < instructions.length; i++) {
//		  instructionPlaces[i] = new Place(i, instructions[i].getInstruction(), icons[instructions[i].getInstructionType()], instructions[i]
//		      .getPoint());
//		}
//		
//		// add route keypoints (instructions) to map
//		
//		map.addPlaces(instructionPlaces);
//		//app.setInstrutionPlaces(instructionPlaces);
//		// recenter map to start of the route
//		// note that YOURS does not have instructions (at least not in current implementation)
////		if(instructionPlaces[0] != null){
////	        map.setMiddlePoint(instructionPlaces[0].getWgs()); 
////	
////	        // start details view
////	        Intent i = new Intent(context, RouteList.class);
////	        context.startActivity(i);
//		}
	}

	@Override
	public void routingErrors(int errorCodes) {
		final StringBuffer errors = new StringBuffer("Errors: ");
        if ((errorCodes & DirectionsService.ERROR_DESTINATION_ADDRESS_NOT_FOUND) != 0) {
            errors.append("destination not found,");
          }
          if ((errorCodes & DirectionsService.ERROR_FROM_ADDRESS_NOT_FOUND) != 0) {
            errors.append("from not found,");
          }
          if ((errorCodes & DirectionsService.ERROR_FROM_AND_DESTINATION_ADDRESS_SAME) != 0) {
            errors.append("from and destination same,");
          }
          if ((errorCodes & DirectionsService.ERROR_ROUTE_NOT_FOUND) != 0) {
            errors.append("route not found,");
          }
          Log.error(errors.toString());
		
	}

	@Override
	public void routingParsingError(String arg0) {
		Log.error("false");		
	}
	
	public WgsPoint getStartCoordinates() {
        return startCoordinates;
	}
	
	public WgsPoint getEndCoordinates() {
	        return endCoordinates;
	}
	
	public void setDirectionsService(final DirectionsService directions) {
        directions.execute();           
	}
	
	public void initialize() {
	        Log.info("Routewaiter initialize");
	}
}
