package com.example.ray.bluetoothsensorsimulator;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class BluetoothConnection extends Activity implements SensorEventListener{

    public static boolean connectionExists = false;
    public static BluetoothSocket BTsocket;
    public static OutputStream BTConnOutput;
    private DataSendingTask SendData; //must execute after BTConnTask is executed first
    private SensorManager mySensorManager;
    private Sensor mySensor;
    private boolean sensor_registered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_connection);
        Log.v("BluetoothConnection", "onCreate");

        Intent intent = getIntent();
        String DeviceName_And_MAC = intent.getStringExtra(MainActivity.EXTRA_MESSAGE);
        final String Name_and_Mac[] = DeviceName_And_MAC.split("\\r?\\n");

        //Set device info
        BluetoothDevice chosen_device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(Name_and_Mac[1]);
        String text = "Name: " + Name_and_Mac[0] +"\n"
                    + "Mac: " + Name_and_Mac[1]  + "\n"
                    + "Class: " + chosen_device.getBluetoothClass().toString() + "\n"
                    + "Sensor Type: Light";
        TextView DeviceText = (TextView) findViewById(R.id.DeviceName);
        DeviceText.setText(text);

        //Set Sensor Reading
        final TextView sensor_reading = (TextView) findViewById(R.id.SensorText);
        sensor_reading.setVisibility(View.GONE);


        //Set send button
        final Button sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setVisibility(View.GONE);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(BluetoothConnection.this, "Sending data...", Toast.LENGTH_LONG).show();

                try {
                    BTConnOutput = BTsocket.getOutputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.v("BTConnActivity", "Fail to obtain input/output stream");
                }

                sensor_registered = true;
                mySensorManager.registerListener(BluetoothConnection.this, mySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });

        //Set connect button
        Button connButton = (Button) findViewById(R.id.Connect_button);
        connButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BTConnTask BTConnection = new BTConnTask();
                BTConnection.execute(Name_and_Mac[1]);
                sendButton.setVisibility(View.VISIBLE);
                sensor_reading.setVisibility(View.VISIBLE);
            }
        });

        mySensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mySensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_connection, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //Every time sensor changes, it will send data to Embedded devices through Bluetooth
        System.out.println(sensorEvent.timestamp);

        TextView sensorText = (TextView) findViewById(R.id.SensorText);

        //Get sensor info
        String sensorName = mySensor.getName();
        String sensorValue =  Float.toString(sensorEvent.values[0]);
        String sensorAccuracy = Integer.toString(sensorEvent.accuracy);

        //Get time
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
        String sensorTime = sdf.format(c.getTime());

        String display_message = "Sensor reading: " + sensorValue + " lx";
        String send_message = "Name: " + sensorName + "   "
                            + "Time: " + sensorTime + "   "
                            + "Accuracy: " + sensorAccuracy + "   "
                            + "Value: " + sensorValue + "\n";

        sensorText.setText(display_message);

        DataSendingTask newDataTask = new DataSendingTask();
        SendData = newDataTask;
        newDataTask.execute(send_message);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //Do nothing if accuracy changed
    }

    @Override
    protected void onStop() {
        Log.v("BTConnActivity", "onStop");
        super.onStop();
        //Cancel data sending task
        if(SendData != null && !SendData.isCancelled())
            SendData.cancel(true);

        //Unregister sensor event
        if(sensor_registered)
        {
            sensor_registered = false;
            mySensorManager.unregisterListener(BluetoothConnection.this, mySensor);
        }

        //Close outputstream
        if(BTConnOutput != null){
            try {
                BTConnOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("DataSendingTask", "Cannot close outputstream");
            }
        }

        //Close Bluetooth Connection task
        if(BluetoothConnection.connectionExists){
            try {
                BluetoothConnection.BTsocket.close();
                BluetoothConnection.connectionExists = false;
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("BTConnActivity", "Failed to close bt connection in onPause");
            }
        }
    }

    //Use AsyncTask to connect to Other devices via SPP
    private class BTConnTask extends AsyncTask<String, String, Boolean>{

        private ProgressDialog dialog;
        private BluetoothAdapter localBTApt;
        private BluetoothSocket tempSocket;

        //Pop up dialog in Main thread to show progress
        @Override
        protected void onPreExecute(){
            dialog = new ProgressDialog(BluetoothConnection.this);
            localBTApt  = BluetoothAdapter.getDefaultAdapter();
            dialog.setMessage("Connecting to Devices...");
            dialog.setCancelable(false);
            dialog.show();
        }

        /*
            Do the actual connection work
            Steps to connect:
            1. Get remote Bluetooth Device
            2. Obtain Bluetooth Socket
            3. Call connect()
         */
        @Override
        protected Boolean doInBackground(String... MACaddr) {
            //Stop job if no Bluetooth device in the Phone or connection already exists
            if(!localBTApt.isEnabled() || BluetoothConnection.connectionExists)
                return false;

                //Step 1:
            final BluetoothDevice remoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MACaddr[0]);

            try {
                //Step 2:
                tempSocket = remoteDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                BluetoothConnection.BTsocket = tempSocket;

                //Step 3:
                BluetoothConnection.BTsocket.connect();

                BluetoothConnection.connectionExists = true;
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    BluetoothConnection.BTsocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    Log.v("BTConnTask", "Unable to close bluetooth socket");
                }
                Log.v("BTConnActivity", "Fail to get Bluetooth Socket from remote devices");
                BluetoothConnection.connectionExists = false;
                return false;
            };

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            dialog.dismiss();
            if(result)
                Toast.makeText(BluetoothConnection.this, "Connection successfully", Toast.LENGTH_LONG).show();
            else
                Toast.makeText(BluetoothConnection.this, "Connection failure", Toast.LENGTH_LONG).show();
        }
    }


    private class DataSendingTask extends AsyncTask<String, Void, Integer>{

        private final Integer task_succeed = 1;
        private final Integer task_failed = 2;
        private final Integer task_cancelled = 3;

        @Override
        protected Integer doInBackground(String... params) {
            if(BTConnOutput == null || BluetoothConnection.BTsocket == null){
                Log.v("DatasendingTask", "Outputstream or BTsocket is empty");
                return task_failed;
            }

            //check if task is cancelled
            if (isCancelled()) {
                Log.v("DataSendingTask", "Task cancelled");
                return task_cancelled;
            }

            //write to devices
            try {
                BTConnOutput.write(params[0].getBytes());
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("BTConnActivity", "Failed to write to outputStream");
                return task_failed;
            }

            return task_succeed;
        }
    }
}

