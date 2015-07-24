/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Author: Deepak Garg
 * Email: me@deepakgarg.me
 */
package com.env.widget.envawidget;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;
import 	android.os.PowerManager;

public class EnvWidget extends AppWidgetProvider {
	
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		
		for(int widgetId : appWidgetIds){
		if(UpdateService.getObject()!=null){
			Toast.makeText(context, "DeRegisteration",
					Toast.LENGTH_LONG).show();
			UpdateService.getObject(). deregisterWidget(widgetId);
		}
		}
		/* remove hidden widgets */
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		AppWidgetHost appWidgetHost = new AppWidgetHost(context, 1); // for removing phantoms
		int[] appWidgetIDs = appWidgetManager.getAppWidgetIds(new ComponentName(context, EnvWidget.class));
		for (int i = 0; i < appWidgetIDs.length; i++) {
		        appWidgetHost.deleteAppWidgetId(appWidgetIDs[i]);
		}
		super.onDeleted(context, appWidgetIds);
	}

	// configuration intent action when user click on the widget
	private static final String CONFIG_CLICKED = "ConfigButtonClick";

	/*
	 * This method is invoked when first instance of widget is added to the home
	 * screen, dont forget to add the intent filter for this in manifest file
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.appwidget.AppWidgetProvider#onEnabled(android.content.Context)
	 */
	@Override
	public void onEnabled(Context context) {
		
		WidgetData.checkAndStartService(context);
		super.onEnabled(context);
	}

	/*
	 * this method is used whenever update is request as defined in the manifest
	 * file if in manifest file it is set to zero then onUpdate will never be
	 * called again (non-Javadoc)
	 * 
	 * @see
	 * android.appwidget.AppWidgetProvider#onUpdate(android.content.Context,
	 * android.appwidget.AppWidgetManager, int[])
	 */
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {

		for(int widgetId : appWidgetIds){
			RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
					R.layout.envwidget);
			/*
			 * we are setting a pending intent on the click so that we can start
			 * a configuration activity
			 */
			Intent intent = new Intent(context, EnvWidget.class);
			intent.setAction(CONFIG_CLICKED);
			remoteViews.setOnClickPendingIntent(R.id.settingsBtnView,
					PendingIntent.getBroadcast(context, 0, intent,
							PendingIntent.FLAG_UPDATE_CURRENT));
			/*
			 * important: we need to fire the updateAppWidget here after
			 * setting the pending intent
			 */
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
			Bundle mAppWidgetOptions= appWidgetManager.getAppWidgetOptions( widgetId);
			
			int height = mAppWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
			if(height<=0){
				height = prefs.getInt("height",0);
			}
			
			if (UpdateService.getObject() != null) {
				if (!UpdateService.getObject().registerWidget(context,
						WidgetTypes.AllWidgets, EnvWidget.class,
						R.layout.envwidget, widgetId,height)) {
					Toast.makeText(context, "unable to add widget",
							Toast.LENGTH_LONG).show();
					}
			}
			if (height > 0) {
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt("height", height); // value to store
				editor.commit();
			}
			//onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, mAppWidgetOptions);
			
			//appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
		super.onUpdate(context, appWidgetManager, appWidgetIds);
	}
	
	@Override
	public void onDisabled(Context context) {

		super.onDisabled(context);
		/*
		 * stop the service when last instance of the widget is removed from the
		 * home screen
		 */
		//context.stopService(new Intent(context, UpdateService.class));
		Toast.makeText(context, "disabled",
				Toast.LENGTH_LONG).show();
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		
		String intentAction = intent.getAction();
		
		if (CONFIG_CLICKED.equals(intentAction)) {
			// remoteViews.setTextViewText(R.id.temp, "1");
			Intent intent1 = new Intent(context, EnvActivity.class);
			// need to set FLAG_ACTIVITY_NEW_TASK as we are starting an activity
			// outside of an activity
			intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			AppWidgetManager appWidgetManager = AppWidgetManager
					.getInstance(context);
			int[] appWidgetIDs = appWidgetManager
					.getAppWidgetIds(new ComponentName(context, EnvWidget.class));
			RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
					R.layout.envwidget);
			
			/* create a click impact */
			remoteViews.setInt(R.id.settingsBtnView, "setAlpha", 10);
			appWidgetManager.partiallyUpdateAppWidget(appWidgetIDs, remoteViews);
			context.startActivity(intent1);
			remoteViews.setInt(R.id.settingsBtnView, "setAlpha", 255);
			appWidgetManager.partiallyUpdateAppWidget(appWidgetIDs, remoteViews);
			
		}
		else if(intentAction.contentEquals("com.sec.android.widgetapp.APPWIDGET_RESIZE")){
			handleTouchWiz(context, intent);
			Toast.makeText(context, intentAction,
					Toast.LENGTH_LONG).show();
		}
		else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(intentAction)) { 
	        final int appWidgetId = intent.getIntExtra 
	(AppWidgetManager.EXTRA_APPWIDGET_ID, 
	                AppWidgetManager.INVALID_APPWIDGET_ID); 
	        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { 
	            this.onDeleted(context, new int[] { appWidgetId }); 
	        } 
	    }
		else if(intentAction.equals("com.env.widget.envawidget.start_registeration")){
			WidgetData.checkAndStartService(context);
			AppWidgetManager appWidgetManager = AppWidgetManager
					.getInstance(context);
			int[] appWidgetIDs = appWidgetManager
					.getAppWidgetIds(new ComponentName(context, EnvWidget.class));
			for (int widgetId : appWidgetIDs) {

				Bundle mAppWidgetOptions = appWidgetManager
						.getAppWidgetOptions(widgetId);

				int height = mAppWidgetOptions
						.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
				if (UpdateService.getObject() != null) {
					UpdateService.getObject().registerWidget(context,
							WidgetTypes.AllWidgets, EnvWidget.class,
							R.layout.envwidget, widgetId, height);
				}

			}
		
		}
		else{
			super.onReceive(context, intent);
		}

	}
	@TargetApi(16)
	private void handleTouchWiz(Context context, Intent intent) {
	  AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

	  int appWidgetId = intent.getIntExtra("widgetId", 0);
	  int widgetSpanX = intent.getIntExtra("widgetspanx", 0);
	  int widgetSpanY = intent.getIntExtra("widgetspany", 0);

	  if (appWidgetId > 0 && widgetSpanX > 0 && widgetSpanY > 0) {
	     Bundle newOptions = new Bundle();
	     // We have to convert these numbers for future use
	     newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, widgetSpanY * 74);
	     newOptions.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widgetSpanX * 74);

	     onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
	  }
	}
	
}
