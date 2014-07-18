package com.example.ray.bluetoothsensorsimulator;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ray.bluetoothsensorsimulator.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothConnection extends Activity {

    public static boolean connectionExists = false;
    public static BluetoothSocket BTsocket;
    private DataSendingTask SendData = new DataSendingTask(); //must execute after BTConnTask is executed first

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
        String text = "Name: " + Name_and_Mac[0] +"\n"+ "Mac: " + Name_and_Mac[1]  + "\n" + "Class: " + chosen_device.getBluetoothClass().toString();
        TextView DeviceText = (TextView) findViewById(R.id.DeviceName);
        DeviceText.setText(text);

        //Set connect button
        Button connButton = (Button) findViewById(R.id.Connect_button);
        connButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BTConnTask BTConnection = new BTConnTask();
                BTConnection.execute(Name_and_Mac[1]);
            }
        });

        //Set send button
        Button sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SendData.execute();
            }
        });

        //Set cancel button
        Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                 SendData.cancel(true);
            }
        });

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
    protected void onPause() {
        super.onPause();

        try {
            BluetoothConnection.BTsocket.close();
            BluetoothConnection.connectionExists = false;
        } catch (IOException e) {
            e.printStackTrace();
            Log.v("BTConnActivity", "Failed to close bt connection in onPause");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SendData.cancel(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SendData.cancel(true);
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
                    Log.v("BTConnActivity", "Unable to close bluetooth socket");
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


    private class DataSendingTask extends AsyncTask<Void, Void, Integer>{

        private OutputStream out = null;
        private final Integer task_succeed = 1;
        private final Integer task_failed = 2;
        private final Integer task_cancelled = 3;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Toast.makeText(BluetoothConnection.this, "Sending data...", Toast.LENGTH_LONG).show();
            try {
                this.out = BluetoothConnection.BTsocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("BTConnActivity", "Fail to obtain input/output stream");
            }
        }

        @Override
        protected Integer doInBackground(Void... Voids) {
            if(out == null || BluetoothConnection.BTsocket == null){
                Log.v("BTConnActivity", "Outputstream or BTsocket is empty");
                return task_failed;
            }

            for(int i=0; i<5; i++){
                //check if task is cancelled
                if(isCancelled()){
                    Log.v("BTConnActivity", "Task cancelled");
                    return task_cancelled;
                }

                if(!isCancelled())
                    System.out.println("Not cancelled");
                else
                    System.out.println("Cancelled");

                //write to devices
                try {
                    out.write(new String("Greetings from ray's Android phone\n").getBytes());
                    Thread.sleep(2000);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.v("BTConnActivity", "Failed to write to outputStream");
                    return task_failed;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.v("BTConnActivity", "Cannot sleep");
                    return task_failed;
                }
            }

            return task_succeed;
        }

        @Override
        protected void onCancelled(Integer result) {
            super.onCancelled(result);
            Log.v("BTConnActivity", "onCancelled");
            Toast.makeText(BluetoothConnection.this, "Send cancelled", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            Log.v("BTConnActivity", "onPostExecute");
            System.out.println(result);

            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.v("BTConnActivity", "Cannot close outputstream");
            }

            switch (result){
                case 1: // Execution successful
                    Toast.makeText(BluetoothConnection.this, "Send successfully", Toast.LENGTH_LONG).show();
                    break;
                case 2: // Execution failed
                    Toast.makeText(BluetoothConnection.this, "Send failed", Toast.LENGTH_LONG).show();
            }

        }
    }
}

