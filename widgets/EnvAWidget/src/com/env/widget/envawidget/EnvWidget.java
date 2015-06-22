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

import com.env.widget.envawidget.EnvWidget.UpdateService.LocalBinder;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;
import 	android.os.PowerManager;
public class EnvWidget extends AppWidgetProvider{
	//configuration intent action
	private static final String CONFIG_CLICKED    = "ConfigButtonClick";
	private UpdateService mService;
    boolean mBound = false;
	//we are overriding onReceive so need not to override onUpdate onDelete etc methods of
	//AppWidgetProvider
	@Override
    public void onReceive(Context context, Intent intent) {
        
        String intentAction = intent.getAction();
        Log.d("recv",intentAction);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.envwidget);
        ComponentName envWidget= new ComponentName(context, EnvWidget.class);
        
        if (CONFIG_CLICKED.equals(intentAction)) {
            //remoteViews.setTextViewText(R.id.temp, "1");
        	Intent intent1 = new Intent(context,EnvActivity.class);
        	//need to set FLAG_ACTIVITY_NEW_TASK as we are starting an activity outside of an activity
        	intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        	context.startActivity(intent1);
            appWidgetManager.updateAppWidget(envWidget, remoteViews);

        }
        else if("android.appwidget.action.APPWIDGET_UPDATE".equals(intentAction))
        {
        	 Log.d("start service",intent.getAction());
        	 
        	 context.getApplicationContext().bindService(new Intent(context, UpdateService.class), mConnection, Context.BIND_AUTO_CREATE);
        	 
             remoteViews.setOnClickPendingIntent(R.id.button, getPendingSelfIntent(context, CONFIG_CLICKED));
             appWidgetManager.updateAppWidget(envWidget, remoteViews);
             context.getApplicationContext().registerReceiver(this,new IntentFilter(Intent.ACTION_SCREEN_ON));
             context.getApplicationContext().registerReceiver(this, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
        else if("android.appwidget.action.APPWIDGET_DELETED".equals(intentAction) ||
        		"android.appwidget.action.APPWIDGET_DISABLED".equals(intentAction) )
        {
        	 
        	 if (mBound) {
        		 Log.d("stop service",intent.getAction());
        		 mService.unRegisterListener();
                 context.getApplicationContext().unbindService(mConnection);
                 mBound = false;
             }
        }
        else if("android.intent.action.SCREEN_OFF".equals(intentAction))
        {
        	//we are unregistering sensor listeners
        	//to save the battery
        	if (mBound) {
        	 mService.unRegisterListener();
        	}
        }
        else if("android.intent.action.SCREEN_ON".equals(intentAction))
        {
        	if (mBound) {
        	mService.registerListener();
        	}
        }
       

    }
	
	protected PendingIntent getPendingSelfIntent(Context context, String action) {
        Intent intent = new Intent(context,EnvWidget.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
	// bind callback
	private ServiceConnection mConnection = new ServiceConnection() {

         @Override
         public void onServiceConnected(ComponentName className,
                 IBinder service) {
             // We've bound to LocalService, cast the IBinder and get LocalService instance
             LocalBinder binder = (LocalBinder) service;
             mService = binder.getService();
             mBound = true;
             Log.d("service","service connexted");
         }

         @Override
         public void onServiceDisconnected(ComponentName arg0) {
             mBound = false;
             Log.d("service","service disconnexted");
             
         }
     };
	public static class UpdateService extends Service implements SensorEventListener,EventListener{
		// init 
		private SensorManager sensormanager=null;
		private Sensor temperature=null;
		private Sensor humidity=null;
		private RemoteViews views = null;
		private ComponentName thisWidget = null;
		private AppWidgetManager manager =null;
		private SharedPreferences SP ;
		private IBinder mBinder = new LocalBinder();
		boolean isTempSensorAvailable = false;
		boolean isHumiditySensorAvailable = false;
		
		// By default unit is Celsius
		int tempUnit=1;
		// By default SENSOR_DELAY_NORMAL (3)
		int upFreq=3;
		@Override
		public void onCreate() {
			super.onCreate();
			SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        	sensormanager = (SensorManager)getSystemService(SENSOR_SERVICE);
        	temperature= sensormanager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        	humidity= sensormanager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        	views = new RemoteViews(this.getPackageName(), R.layout.envwidget);
        	thisWidget = new ComponentName(this, EnvWidget.class);
        	manager = AppWidgetManager.getInstance(this);
        	Log.d("service","oncreatecalled");
        	EnvActivity.RegisterForEvent(this);
        	registerListener();
			if(!isTempSensorAvailable)
			{
				views.setTextViewText(R.id.temp,"NS");
			}
			if(!isHumiditySensorAvailable)
			{
				views.setTextViewText(R.id.humidity,"NS");
			}
			manager.updateAppWidget(thisWidget, views);
		}
		@Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }
        @Override
		public void onDestroy() {
			// TODO Auto-generated method stub
			super.onDestroy();
			Log.d("service","ondestroy");
			unRegisterListener();
		}

		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onSensorChanged(SensorEvent arg0) {
			// TODO Auto-generated method stub
			
			if( arg0.sensor.getType()==Sensor.TYPE_AMBIENT_TEMPERATURE)
			{
				double val = arg0.values[0];
				String temp="";
				if(tempUnit==1)
				{
					temp=Math.round(val)+"\u2103";
				}
				else
				{
					val = val*1.8000+32.00;
					temp=Math.round(val)+"\u2109";
				}
				views.setTextViewText(R.id.temp,temp);
			} 
			else
			{
				views.setTextViewText(R.id.humidity,Math.round(arg0.values[0])+"%");
			}
			int[] ids=manager.getAppWidgetIds(thisWidget);
			for(int i=0; i<ids.length; i++)
			{
			 manager.updateAppWidget(ids[i], views);
			}
	       
		}

		@Override
		public void settingsChanged(Settings[] type) {
			
			for(int i=0; i<type.length;i++)
			{
				switch (type[i].getSType()) {
				case TEMP_UNIT:
					tempUnit = type[i].getVal();
					break;
				case UP_FREQ:
					// De-register and Re-Register the sensors
					//TODO cleaner way to do this to implement a runnable
					sensormanager.unregisterListener(this, temperature);
					sensormanager.unregisterListener(this, humidity);
					sensormanager.registerListener(this, temperature, type[i].getVal());
					sensormanager.registerListener(this, humidity, type[i].getVal());
					upFreq = type[i].getVal();
					break;

				}
			}
			
		}
		
		public void unRegisterListener()
		{
			Log.d("service","unregister listeners");
			sensormanager.unregisterListener(this, temperature);
			sensormanager.unregisterListener(this, humidity);
		}
		public void registerListener()
		{
			
			if(temperature!=null)
        	{
				isTempSensorAvailable=true;
        		sensormanager.registerListener(UpdateService.this, temperature, SensorManager.SENSOR_DELAY_NORMAL);
        	}
        	if(humidity!=null)
        	{
        		isHumiditySensorAvailable = true;
        		sensormanager.registerListener(UpdateService.this, humidity, SensorManager.SENSOR_DELAY_NORMAL);
        	}
		}
	
		class LocalBinder extends Binder {
	        UpdateService getService() {
	            // Return this instance of LocalService so clients can call public methods
	            return UpdateService.this;
	        }
	    }
		 
    }
	
}
