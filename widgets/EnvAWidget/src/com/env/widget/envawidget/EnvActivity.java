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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;


public class EnvActivity extends PreferenceActivity  {
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
		setFinishOnTouchOutside(false); 
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Intent intent = this.getIntent();
		this.setResult(RESULT_OK, intent);
		finish();
	}
	@Override
	protected void onStop() {
		super.onStop();
		
	}
	
}
