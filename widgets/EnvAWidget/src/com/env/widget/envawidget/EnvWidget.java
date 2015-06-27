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

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;
import 	android.os.PowerManager;

public class EnvWidget extends AppWidgetProvider {
	// configuration intent action when user click on the widget
	private static final String CONFIG_CLICKED = "ConfigButtonClick";

	/*
	 * this method is invoked when first instance of widget is added to the home
	 * screen, dont forget to add the intent filter for this in manifest file
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.appwidget.AppWidgetProvider#onEnabled(android.content.Context)
	 */
	@Override
	public void onEnabled(Context context) {

		super.onEnabled(context);
		context.startService(new Intent(context, UpdateService.class));
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

		super.onUpdate(context, appWidgetManager, appWidgetIds);
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
			UpdateService.buildUpdate(context, remoteViews,
					context.getString(R.string.thermicon), R.id.tempIconView,
					100, 220, 10, 215, 200,Color.WHITE);
			UpdateService.buildUpdate(context, remoteViews,
					context.getString(R.string.humidityicon),
					R.id.humidityIconView, 100, 220, 10, 215, 200,Color.WHITE);
			UpdateService.buildUpdate(context, remoteViews,
					context.getString(R.string.settingsicon),
					R.id.settingsBtnView, 200, 230, 0, 180, 170,Color.GRAY);
			
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
	}
	
	@Override
	public void onDisabled(Context context) {

		super.onDisabled(context);
		/*
		 * stop the service when last instance of the widget is removed from the
		 * home screen
		 */
		context.stopService(new Intent(context, UpdateService.class));
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		String intentAction = intent.getAction();

		if (CONFIG_CLICKED.equals(intentAction)) {
			// remoteViews.setTextViewText(R.id.temp, "1");
			Intent intent1 = new Intent(context, EnvActivity.class);
			// need to set FLAG_ACTIVITY_NEW_TASK as we are starting an activity
			// outside of an activity
			
			intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
					| Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK);
			context.startActivity(intent1);
		}

	}

	public static class UpdateService extends Service implements
			SensorEventListener,
			SharedPreferences.OnSharedPreferenceChangeListener {
		// init
		private SensorManager sensormanager = null;
		private Sensor temperature = null;
		private Sensor humidity = null;

		SharedPreferences SP;
		boolean isTempSensorAvailable = false;
		boolean isHumiditySensorAvailable = false;
		// By default unit is Celsius
		int tempUnit = 1;
		// By default SENSOR_DELAY_NORMAL (3)
		int upFreq = 3;
		RemoteViews views = null;
		ComponentName thisWidget = null;
		AppWidgetManager manager = null;
		Context ctx ;
		@Override
		public void onCreate() {
			super.onCreate();
			SP = PreferenceManager
					.getDefaultSharedPreferences(getBaseContext());
			sensormanager = (SensorManager) getSystemService(SENSOR_SERVICE);
			temperature = sensormanager
					.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
			humidity = sensormanager
					.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
			thisWidget = new ComponentName(this, EnvWidget.class);
			manager = AppWidgetManager.getInstance(this);
			/*
			 * registering broadcast receiver for screen_on and screen_off
			 * intents
			 */
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			filter.addAction(Intent.ACTION_SCREEN_ON);
			registerReceiver(mReceiver, filter);
		}

		@Override
		public int onStartCommand(Intent intent, int flags, int startId) {
			registerListener(SensorManager.SENSOR_DELAY_NORMAL);
			PreferenceManager.getDefaultSharedPreferences(this)
					.registerOnSharedPreferenceChangeListener(this);
			applySettings(SP);
			ctx=this;
			int[] widgetIds = manager.getAppWidgetIds(thisWidget);
			for (int widgetId : widgetIds) {
				views = new RemoteViews(this.getPackageName(),
						R.layout.envwidget);
				if (!isTempSensorAvailable) {
					views.setTextViewText(R.id.temp, "NS");
				}
				if (!isHumiditySensorAvailable) {
					views.setTextViewText(R.id.humidity, "NS");
				}

				manager.updateAppWidget(widgetId, views);
			}

			return super.onStartCommand(intent, flags, startId);
		}

		@Override
		public IBinder onBind(Intent intent) {
			return null;
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
			unRegisterListener();
			unregisterReceiver(mReceiver);
		}

		@Override
		public void onAccuracyChanged(Sensor arg0, int arg1) {

		}

		@Override
		public void onSensorChanged(SensorEvent arg0) {
			
			int[] widgetIds = manager.getAppWidgetIds(thisWidget);
			for (int widgetId : widgetIds) {
				views = new RemoteViews(this.getPackageName(),
						R.layout.envwidget);
				//buildUpdate(this,views,getString(R.string.thermicon),R.id.tempIconView);
				//buildUpdate(this,views,getString(R.string.humidityicon),R.id.humidityIconView);
				int temperature=0;
				int humidity=0;
				if (arg0.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
					double val = arg0.values[0];
					String temp = "";
					if (tempUnit == 1) {
						temperature = (int)Math.round(val);
						temp = temperature + "\u2103";
					} else {
						val = val * 1.8000 + 32.00;
						temp = Math.round(val) + "\u2109";
					}
					views.setTextViewText(R.id.temp, temp);
				} else {
					humidity=(int)Math.round(arg0.values[0]);
					views.setTextViewText(R.id.humidity,
							humidity+ "%");
				}
				
				if(temperature>23 && temperature <26)
				{
					//env is good
					buildUpdate(this,views,
							getString(R.string.smile), R.id.moodView,
							185, 230, 10, 205, 155,Color.WHITE);
					views.setTextViewText(R.id.moodString, "Good!");
					
				}
				else if(temperature > 26 && temperature <30)
				{
					buildUpdate(this,views,
							getString(R.string.neutral), R.id.moodView,
							185, 230, 10, 205, 155,Color.WHITE);
					views.setTextViewText(R.id.moodString, "Ok!");
					
				}
				else if(temperature > 30)
				{
					buildUpdate(this,views,
							getString(R.string.sad), R.id.moodView,
							185, 230, 10, 205, 155,Color.WHITE);
					views.setTextViewText(R.id.moodString, "Hot!");
				}
				
				Intent intent = new Intent(this, EnvWidget.class);
				intent.setAction(CONFIG_CLICKED);
				
				views.setOnClickPendingIntent(R.id.settingsBtnView,
						PendingIntent.getBroadcast(this, 0, intent,
								PendingIntent.FLAG_UPDATE_CURRENT));
				
				manager.updateAppWidget(widgetId, views);
			}
		}

		public void applySettings(SharedPreferences arg0) {

			String tempU = arg0.getString("tempUnit", "1");
			String upF = arg0.getString("upFreq", "3");
			Settings[] type = {
					new Settings(SType.TEMP_UNIT, new Integer(tempU)),
					new Settings(SType.UP_FREQ, new Integer(upF)) };
			for (int i = 0; i < type.length; i++) {
				switch (type[i].getSType()) {
				case TEMP_UNIT:
					tempUnit = type[i].getVal();
					break;
				case UP_FREQ:
					// De-register and Re-Register the sensors
					unRegisterListener();
					registerListener(type[i].getVal());
					upFreq = type[i].getVal();
					break;

				}
			}

		}

		public void unRegisterListener() {
			sensormanager.unregisterListener(this, temperature);
			sensormanager.unregisterListener(this, humidity);
		}

		public void registerListener(int delay) {

			if (temperature != null) {
				isTempSensorAvailable = true;
				sensormanager.registerListener(UpdateService.this, temperature,
						delay);
			}
			if (humidity != null) {
				isHumiditySensorAvailable = true;
				sensormanager.registerListener(UpdateService.this, humidity,
						delay);
			}
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences arg0,
				String arg1) {

			applySettings(arg0);
		}

		private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String intentAction = intent.getAction();
				if ("android.intent.action.SCREEN_OFF".equals(intentAction)) {
					// we are unregistering the sensors as device screen goes off
					// to save the battery
					unRegisterListener();
				} else if ("android.intent.action.SCREEN_ON"
						.equals(intentAction)) {
					registerListener(new Integer(
							(PreferenceManager
									.getDefaultSharedPreferences(context)
									.getString("upFreq", "3"))));
				}
			}
		};

		public static void buildUpdate(Context context, RemoteViews views,
				String text, int viewId,int w1,int h1,int x,int y,int size,int color) {
			Bitmap myBitmap = Bitmap.createBitmap(w1, h1,
					Bitmap.Config.ARGB_4444);
			Canvas myCanvas = new Canvas(myBitmap);
			Paint paint = new Paint();
			Typeface clock = Typeface.createFromAsset(context.getAssets(),
					"fonts/weather.ttf");
			paint.setAntiAlias(true);
			paint.setSubpixelText(true);
			paint.setTypeface(clock);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(color);
			paint.setTextSize(size);
			myCanvas.drawText(text, x, y, paint);
			views.setImageViewBitmap(viewId, myBitmap);
		}
	}

}
