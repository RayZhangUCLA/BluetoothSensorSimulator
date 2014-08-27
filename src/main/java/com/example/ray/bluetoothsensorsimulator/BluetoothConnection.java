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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
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
    private time_and_reading[] reading_array = new time_and_reading[10];
    private int num_readings = 0;
    private short type_mask_info = 0x7FFF;
    private byte type_mask_data = (byte) 0x80;
    private byte float_mask = (byte) 0xBF;
    private byte double_mask = (byte) 0x40;

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

                sensor_registered = true;
                mySensorManager.registerListener(BluetoothConnection.this, mySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });

        //Set connect button
        Button connButton = (Button) findViewById(R.id.Connect_button);
        connButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mySensor != null){
                    //Establish bluetooth connection
                    BTConnTask BTConnection = new BTConnTask();
                    BTConnection.execute(Name_and_Mac[1]);
                    sendButton.setVisibility(View.VISIBLE);
                    sensor_reading.setVisibility(View.VISIBLE);

                    System.out.println("Connection established");

                    SystemClock.sleep(2000);

                    //Obtain BT connection Ouputstream
                    try {
                        BTConnOutput = BTsocket.getOutputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.v("BTConnActivity", "Fail to obtain input/output stream");
                    }

                    //Sending Metadata to Device
                    String sensorName = mySensor.getName();
                    String sinfo = Build.DEVICE + "\t"
                            + sensorName + "\t"
                            + "lx";
                    System.out.println(String.format("%040x", new BigInteger(1, sinfo.getBytes())));
                    short len = (short) sinfo.length();
                    System.out.println(len);

                    if(len <= 32768) {
                        //Constructing Sensor info packet
                        System.out.println("Constructing metadata...");

                        short temp = (short) (len & type_mask_info);
                        System.out.println(temp);
                        byte[] type_len = new byte[2];
                        type_len[0] = (byte) (temp & 0xFF);
                        type_len[1] = (byte) ((temp >> 8) & 0xFF);

                        byte[] data_send = new byte[1 + 2 + temp + 1];

                        data_send[0] = (byte) 0x02; //copy STX
                        data_send[1] = type_len[1]; //copy TYPE and LEN
                        data_send[2] = type_len[0];
                        System.arraycopy(sinfo.getBytes(), 0, data_send, 3, temp); //copy SINFO
                        data_send[1+2+temp] = (byte) 0x03; //copy ETX


                        DataSendingTask newDataTask = new DataSendingTask();
                        SendData = newDataTask;
                        newDataTask.execute(data_send);
                    }
                    else {
                        Toast.makeText(BluetoothConnection.this, "Cannot send sensor information. Information is too big", Toast.LENGTH_LONG).show();
                    }

                }else {
                    Toast.makeText(BluetoothConnection.this, "Cannot connect, you do not have light sensors on your phone.", Toast.LENGTH_LONG).show();
                }
            }
        });

        mySensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mySensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if(mySensor == null)
            Toast.makeText(BluetoothConnection.this, "Sorry, you do not have light sensors on your phone.", Toast.LENGTH_LONG).show();
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
        //It will send data to Embedded devices through Bluetooth when having 10 readings
        System.out.println(sensorEvent.timestamp);

        TextView sensorText = (TextView) findViewById(R.id.SensorText);

        //Get sensor info
        float sensorValue =  sensorEvent.values[0];

        //Get time
        long timestamp = System.currentTimeMillis()/1000;
        System.out.print("timestamp = ");
        System.out.print(timestamp);

        String display_message = "Sensor reading: " + sensorValue + " lx";
        System.out.println("Getting readings");

        // Only send data when we have 10 readings
        if(num_readings < 10){
            reading_array[num_readings]= new time_and_reading();
            reading_array[num_readings].ts = timestamp;
            reading_array[num_readings].reading = sensorValue;
            num_readings++;
        }
        else{
            //Constructing data packet
            byte[] data_send = new byte[1+1+10*12+1];

            data_send[0] = (byte) 0x02; //STX
            data_send[1] = (byte )( (0x0A | type_mask_data) & float_mask); //1 for data, 0 for float, A for NOR

            int reading_index = 2;
            for(int i=0; i<num_readings; i++){
                //construct (ts, reading) tuple in byte array
                byte[] ts_array = ByteBuffer.allocate(8).putLong(reading_array[i].ts).array();
                byte[] rd_array = ByteBuffer.allocate(4).putFloat(reading_array[i].reading).array();
                byte[] tuple_array = new byte[12];
                System.arraycopy(ts_array, 0, tuple_array, 0, 8);
                System.arraycopy(rd_array, 0, tuple_array, 8, 4);

                //copy to data send
                System.arraycopy(tuple_array, 0, data_send, reading_index, 12);
                reading_index += 12;
            }

            data_send[1+1+10*12] = (byte) 0x03;

            //Clear data
            num_readings = 0;
            reading_array = new time_and_reading[10];

            DataSendingTask newDataTask = new DataSendingTask();
            SendData = newDataTask;
            newDataTask.execute(data_send);
            }

        System.out.println("Reading obtained");
        sensorText.setText(display_message);
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
                //tempSocket = remoteDevice.createInsecureRfcommSocketToServiceRecord(UUID.randomUUID());
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


    private class DataSendingTask extends AsyncTask<byte[], Void, Integer>{

        private final Integer task_succeed = 1;
        private final Integer task_failed = 2;
        private final Integer task_cancelled = 3;

        @Override
        protected Integer doInBackground(byte[]... params) {
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
                BTConnOutput.write(params[0]);
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("BTConnActivity", "Failed to write to outputStream");
                return task_failed;
            }

            return task_succeed;
        }
    }

    private class time_and_reading{
        long ts;
        float reading;
    }
}