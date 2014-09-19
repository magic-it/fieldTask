package loaders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


public class SmapTaskObserver extends BroadcastReceiver {

	  private SmapTaskLoader mLoader;
	  
public SmapTaskObserver(SmapTaskLoader loader) {
    mLoader = loader;
	
    LocalBroadcastManager.getInstance(mLoader.getContext()).registerReceiver(this,
  	      new IntentFilter("refresh"));
  }

  @Override
  public void onReceive(Context context, Intent intent) {
	  Log.i("SmapTaskObserver: ", "++++++++received refresh");
	  mLoader.onContentChanged();
  }
}
