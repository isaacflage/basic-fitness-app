/*
 * Copyright (C) 2016 Google, Inc.
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
package com.google.android.gms.fit.samples.stepcounter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.fit.samples.common.logger.Log;
import com.google.android.gms.fit.samples.common.logger.LogView;
import com.google.android.gms.fit.samples.common.logger.LogWrapper;
import com.google.android.gms.fit.samples.common.logger.MessageOnlyLogFilter;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
//import com.google.firebase.database.DatabaseReference;

import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.Calendar;

/**
 * This sample demonstrates combining the Recording API and History API of the Google Fit platform
 * to record steps, and display the daily current step count. It also demonstrates how to
 * authenticate a user with Google Play Services.
 */
public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_OAUTH_REQUEST_CODE = 0x1001;
  int previous_steps = 0;
  public String signedIn;
  public TextView textView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    textView = (TextView) findViewById(R.id.textView);
    ActionBar bar = getSupportActionBar();
    bar.setBackgroundDrawable(new ColorDrawable(0xff23729a));
    bar.setTitle(Html.fromHtml("<font color=\"#FFFFFF\">" + getString(R.string.app_name) + "</font>"));


    FitnessOptions fitnessOptions =
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .build();
    if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
      GoogleSignIn.requestPermissions(
          this,
          REQUEST_OAUTH_REQUEST_CODE,
          GoogleSignIn.getLastSignedInAccount(this),
          fitnessOptions);
    } else {
      subscribe();
    }

    Account[] accounts = AccountManager.get(this).getAccounts();
    if(accounts != null && accounts.length > 0) {
      ArrayList playAccounts = new ArrayList();
      for (Account account : accounts) {
        signedIn = account.name;

      }

    }
  }

  private void startSignInIntent() {
    GoogleSignInClient signInClient = GoogleSignIn.getClient(this,
            GoogleSignInOptions.DEFAULT_SIGN_IN);
    Intent intent = signInClient.getSignInIntent();
    startActivityForResult(intent, 1);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
        subscribe();
      }
    }

  }

  /** Records step data by requesting a subscription to background step data. */
  public void subscribe() {
    // To create a subscription, invoke the Recording API. As soon as the subscription is
    // active, fitness data will start recording.
    Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this))
        .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
        .addOnCompleteListener(
            new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  textView.append("\n Subscribed");
                } else {
                  textView.append("\n There was a problem subscribing." + task.getException());
                }
              }
            });
  }

  /**
   * Reads the current daily step total, computed from midnight of the current day on the device's
   * current timezone.
   */
  private void readData() {
    Fitness.getHistoryClient(this, GoogleSignIn.getLastSignedInAccount(this))
        .readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
        .addOnSuccessListener(
            new OnSuccessListener<DataSet>() {
              @Override
              public void onSuccess(DataSet dataSet) {
                long total =
                    dataSet.isEmpty()
                        ? 0
                        : dataSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
                textView.setText("\n Total steps: " + total);
                previous_steps = (int)total;
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                textView.setText("\n There was a problem getting the step count." + e);
              }
            });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the main; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu);
    return true;

  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.action_read_data) {
      readData();
      return true;
    }
    else if (id == R.id.upload_data){
      displayOldWorkout();
      return true;
    }
    else if (id == R.id.compare_data){
      compareData();
      return true;
    }
    else if (id == R.id.display_history){
      displayHistory();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }


  Date currentTime = Calendar.getInstance().getTime();

  DateFormat df = new SimpleDateFormat("MM/dd/yyyy, HH:mm");

  private void displayOldWorkout(){
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    Map<String, Object> user = new HashMap<>();
    user.put("email", signedIn);
    user.put("steps", previous_steps);
    user.put("date", df.format(currentTime));

    // Add a new document with a generated ID
    db.collection("workouts")
            .add(user)
            .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
              @Override
              public void onSuccess(DocumentReference documentReference) {
                textView.setText("\n Successfully uploaded data.");
              }
            })
            .addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                textView.append("\n Error adding document" + e);
              }
            });
  }
  ArrayList<String> userSteps = new ArrayList<String>();
  ArrayList<Integer> steps  = new ArrayList<Integer>();


  private void compareData(){
    //Read in other users data
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    db.collection("workouts")
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
              @Override
              public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                  for (DocumentSnapshot document : task.getResult()) {
                    userSteps.add(document.get("steps").toString());
                  }

                  for(int x = 0; x < userSteps.size(); x++){
                    steps.add(Integer.valueOf(userSteps.get(x)));
                  }
                  bubbleSort(steps);

                  DecimalFormat df = new DecimalFormat("#,###,##0.00");

                  double percent = ((steps.indexOf(previous_steps)) / (float)steps.size()) * 100;
                  textView.setText("\n You are beating " + df.format(percent)  + "% of users. Keep it up!");


                } else {
                  textView.setText("\n Error getting documents: " + task.getException());
                }
              }
            });




  }

  private void displayHistory(){
    //Read in other users data
    textView.setText("\n Previous Workouts:");
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    db.collection("workouts")
            .whereEqualTo("email", signedIn)
            .get()
            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
              @Override
              public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                  for (DocumentSnapshot document : task.getResult()) {
                    textView.append("\n Date: " + document.get("date") + "\n\t Steps: "+ document.get("steps"));
                  }
                } else {
                  textView.append("\n Error getting documents: " + task.getException());
                }
              }
            });

  }

  private void bubbleSort(ArrayList<Integer> arr)
  {
    int n = arr.size();
    for (int i = 0; i < n-1; i++)
      for (int j = 0; j < n-i-1; j++)
        if (arr.get(j) > arr.get(j+1))
        {
          // swap arr[j+1] and arr[i]
          int temp = arr.get(j);
          arr.set(j, arr.get(j+1));
          arr.set(j+1, temp);
        }
  }




}
