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
		private final String dCel ="\u2103";
		private final String dFrahn ="\u2109";
		private SensorManager sensormanager = null;
		private Sensor temperature = null;
		private Sensor humidity = null;
		private 
		SharedPreferences SP;
		private boolean isTempSensorAvailable = false;
		private boolean isHumiditySensorAvailable = false;
		// By default unit is Celsius
		private int tempUnit = 1;
		// By default SENSOR_DELAY_NORMAL (3)
		private int upFreq = 3;
		private RemoteViews views = null;
		private ComponentName thisWidget = null;
		private AppWidgetManager manager = null;
		private Context ctx ;
		private double tempVal=0;
		private double humidityVal=0;
		private double feelsLike=0;
		private double dewpoint=0;
		private  double[] HIConstants={-42.379,2.04901523,10.14333127,-0.22475541,
				-0.00683783,-0.05481717,0.00122874,0.00085282,-0.00000199};
		boolean isSettingChanged =false;
		private  float scale=0;
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
			/* get the screen density
			 * 
			 */
			 scale = getResources().getDisplayMetrics().density;
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
			ctx = this;
			views = new RemoteViews(this.getPackageName(), R.layout.envwidget);
			if (!isTempSensorAvailable) {
				views.setTextViewText(R.id.temp, "NS");
			}
			if (!isHumiditySensorAvailable) {
				views.setTextViewText(R.id.humidity, "NS");
			}
			buildUpdate(this, views, getString(R.string.thermicon),
					R.id.tempIconView, 100, 200, 10, 215, (int) (50 * scale),
					Color.WHITE);
			buildUpdate(this, views, getString(R.string.humidityicon),
					R.id.humidityIconView, 110, 200, 10, 235,
					(int) (50 * scale), Color.WHITE);
			buildUpdate(this, views, getString(R.string.dewpointicon),
					R.id.dewPointIconView, 110, 200, 10, 235,
					(int) (50 * scale), Color.WHITE);
			buildUpdate(this, views, getString(R.string.settingsicon),
					R.id.settingsBtnView, 150, 230, 0, 180, (int) (30 * scale),
					Color.GRAY);
			manager.updateAppWidget(manager.getAppWidgetIds(thisWidget), views);

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

			
			double curTempVal = 0;
			double curHumidityVal = 0;
			boolean updateTempFlag = false;
			boolean updateHumFlag = false;
			views = new RemoteViews(this.getPackageName(), R.layout.envwidget);
			if (arg0.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
				curTempVal = arg0.values[0];
				if ((int) tempVal != (int) curTempVal) {
					tempVal = curTempVal;
					updateTempFlag = true;
				}
			} else {
				curHumidityVal = arg0.values[0];
				if ((int) humidityVal != (int) curHumidityVal) {
					humidityVal = curHumidityVal;
					updateHumFlag = true;
				}
			}

			/*
			 * we should update only when there is a change
			 */
			if (updateTempFlag || updateHumFlag) {

				feelsLike = calculateHI(tempVal * 1.8000 + 32.00, humidityVal);
				dewpoint = calculateDewPoint(tempVal, humidityVal);
				if (feelsLike > 23 && feelsLike < 26) {
					// env is good
					buildUpdate(this, views, getString(R.string.smile),
							R.id.moodView, 185, 210, 10, 200,
							(int) (50 * scale), Color.WHITE);

				} else if (feelsLike >= 26 && feelsLike < 30) {
					buildUpdate(this, views, getString(R.string.neutral),
							R.id.moodView, 185, 210, 10, 200,
							(int) (50 * scale), Color.WHITE);

				} else if (feelsLike >= 30) {
					buildUpdate(this, views, getString(R.string.sad),
							R.id.moodView, 185, 210, 10, 200,
							(int) (50 * scale), Color.WHITE);
				}

				if (tempUnit == 1) {
					views.setTextViewText(R.id.temp, (int) Math.round(tempVal)
							+ dCel);
					views.setTextViewText(R.id.moodString, "Feels: "
							+ (int) Math.round(feelsLike) + dCel);
					views.setTextViewText(R.id.dewPoint,
							(int) Math.round(dewpoint) + dCel);
				} else {
					views.setTextViewText(R.id.temp,
							(int) Math.round(tempVal * 1.8000 + 32.00) + dFrahn);
					views.setTextViewText(R.id.moodString, "Feels: "
							+ (int) Math.round(feelsLike * 1.8000 + 32.00)
							+ dFrahn);
					views.setTextViewText(R.id.dewPoint,
							(int) Math.round(dewpoint* 1.8000 + 32.00) + dFrahn);
				}
				views.setTextViewText(R.id.humidity,
						(int) Math.round(humidityVal) + "%");
				
				
				
				Intent intent = new Intent(this, EnvWidget.class);
				intent.setAction(CONFIG_CLICKED);
				views.setOnClickPendingIntent(R.id.settingsBtnView,
						PendingIntent.getBroadcast(this, 0, intent,
								PendingIntent.FLAG_UPDATE_CURRENT));

				views.setTextViewText(R.id.lastUpdated, DateFormat
						.getDateTimeInstance().format(new Date()));
				manager.partiallyUpdateAppWidget(
						manager.getAppWidgetIds(thisWidget), views);
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

			views = new RemoteViews(this.getPackageName(), R.layout.envwidget);
			if (tempUnit == 1) {
				views.setTextViewText(R.id.temp, (int) Math.round(tempVal)
						+ dCel);
				views.setTextViewText(R.id.moodString,
						"Feels: " + (int) Math.round(feelsLike) + dCel);
				views.setTextViewText(R.id.dewPoint,
						(int) Math.round(dewpoint) + dCel);
			} else {
				views.setTextViewText(R.id.temp,
						(int) Math.round(tempVal * 1.8000 + 32.00) + dFrahn);
				views.setTextViewText(
						R.id.moodString,
						"Feels: "
								+ (int) Math.round(feelsLike * 1.8000 + 32.00)
								+ dFrahn);
				views.setTextViewText(R.id.dewPoint,
						(int) Math.round(dewpoint* 1.8000 + 32.00) + dFrahn);
			}
			manager.partiallyUpdateAppWidget(
					manager.getAppWidgetIds(thisWidget), views);

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

		public void buildUpdate(Context context, RemoteViews views,
				String text, int viewId,int w1,int h1,int x,int y,int size,int color) {
			int shadowWidth=25;
			Paint paint = new Paint();
			Typeface clock = Typeface.createFromAsset(context.getAssets(),
					"fonts/weather.ttf");
			paint.setAntiAlias(true);
			paint.setSubpixelText(true);
			paint.setTypeface(clock);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(color);
			//setTextSizeForWidth(paint,w1,h1,text);
			paint.setTextSize(size);
			paint.setShadowLayer(5.0f, 10.0f, 10.0f, Color.BLACK);
			Rect bounds = new Rect();
			paint.getTextBounds(text, 0, text.length(), bounds);
			paint.getFontSpacing();
			Bitmap myBitmap = Bitmap.createBitmap(bounds.width()+shadowWidth/*shadow x+y*/, (int)(paint.getFontSpacing()+5),
					Bitmap.Config.ARGB_8888);
			android.graphics.Bitmap.Config bitmapConfig =
				      myBitmap.getConfig();
			myBitmap = myBitmap.copy(bitmapConfig, true);
			Canvas myCanvas = new Canvas(myBitmap);
			;
			int m = (shadowWidth/2)-5;
			int n = (int)(paint.getFontSpacing());
			myCanvas.drawText(text,m,n, paint);
			views.setImageViewBitmap(viewId, myBitmap);
		}
		
		private double calculateHI(double temp,double humidity){
			double heatIndex = 0.0;
			heatIndex = HIConstants[0]+ 
						HIConstants[1]*temp+
						HIConstants[2]*humidity+
						HIConstants[3]*temp*humidity+
						HIConstants[4]*temp*temp+
						(HIConstants[5]*humidity)*humidity+
						(HIConstants[6]*temp*temp)*humidity+
						(HIConstants[7]*temp*humidity)*humidity+
						(HIConstants[8]*temp*temp)*humidity*humidity;
			heatIndex = (heatIndex-32.00)/1.800;
			return heatIndex;
		}
		private double calculateDewPoint(double temp,double humidity){
			double dewPoint = 0.0;
			double m=17.62;
			double tn= 243.12;
			double a=Math.log(humidity/100);
			double b=m*temp/(tn+temp);
			dewPoint = tn*(a+b)/m-(a+b);
			return dewPoint;
		}
	}

}
