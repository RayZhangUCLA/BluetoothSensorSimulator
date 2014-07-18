package com.example.ray.bluetoothsensorsimulator;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.Set;


/*
    This app is intended to simulate a Bluetooth Light Sensor that uses SPP profile
    to send data to an Embedded Linux Device(eg. BeagleBone Black) on an Android phone.

    References: http://examples.javacodegeeks.com/android/core/bluetooth/bluetoothadapter/android-bluetooth-example/
                http://developer.android.com/guide/topics/connectivity/bluetooth.html#SettingUp
                https://github.com/hzjerry/BluetoothSppPro/tree/68d025eab4cedae05b969892427f6a46f4021b5a
 */

public class MainActivity extends Activity {

    private static final int REQUEST_ENABLE_BT = 1;
    private Button BTswitch, ListPair, Scan;
    private TextView status;
    private ListView listView;
    private BluetoothAdapter myBTAdapter;
    private Set<BluetoothDevice> pairedDevices;
    private ArrayAdapter<String> BTArrayAdapter;
    public final static String EXTRA_MESSAGE = "com.example.ray.bluetoothsensorsimulator.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.v("MainActivity", "onCreate");

        //Setting Up Bluetooth
        myBTAdapter = BluetoothAdapter.getDefaultAdapter();
        if(myBTAdapter == null){
            // Device does not support Bluetooth
            BTswitch.setEnabled(false);
            ListPair.setEnabled(false);
            Scan.setEnabled(false);
            status.setText("Status:not supported");
            Toast.makeText(MainActivity.this, "Your Phone does not support Bluetooth", Toast.LENGTH_LONG).show();
        }
        else {
            //Enable Bluetooth
            if(!myBTAdapter.isEnabled()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        status = (TextView) findViewById(R.id.Status);
        if(myBTAdapter.isEnabled())
            status.setText("Status: Enabled");

        BTswitch = (Button) findViewById(R.id.Switch_button);
        //Turn on/off the bluetooth module
        BTswitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v("Button", "Switch button clicked");
                if(myBTAdapter.isEnabled()){
                    myBTAdapter.disable();
                    status.setText("Status: Disconnected");
                    Toast.makeText(MainActivity.this, "Bluetooth is turned off", Toast.LENGTH_LONG).show();
                }
                else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }

            }
        });

        //List paired Devices
        ListPair = (Button) findViewById(R.id.Paird_button);
        ListPair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v("Button", "Paired button clicked");
                //clear listview
                BTArrayAdapter.clear();
                pairedDevices = myBTAdapter.getBondedDevices();
                for(BluetoothDevice device : pairedDevices){
                    BTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
        });

        //Scan for Bluetooth devices
        Scan = (Button) findViewById(R.id.Scan_button);
        Scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v("Button", "Scan button clicked");
                if(myBTAdapter.isDiscovering()) {
                    myBTAdapter.cancelDiscovery();
                }
                BTArrayAdapter.clear();
                registerReceiver(myReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                myBTAdapter.startDiscovery();
            }
        });

        //Set up Listview
        listView = (ListView) findViewById(R.id.main_listview);
        BTArrayAdapter = new ArrayAdapter<String>(this, R.layout.listviewargument);
        listView.setAdapter(this.BTArrayAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View viewClicked, int i, long l) {
                TextView textClicked = (TextView) viewClicked;
                String message = textClicked.getText().toString();
                Toast.makeText(MainActivity.this, "You choose Device " + message.replace(System.getProperty("line.separator")," ") + ".", Toast.LENGTH_LONG).show();

                //Start BluetoothConnection Activity
                Intent intent = new Intent(MainActivity.this, BluetoothConnection.class);
                intent.putExtra(EXTRA_MESSAGE, message);
                startActivity(intent);
            }
        });
    }


    //Broadcast Receiver for scanning devices
    private final BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //When discovery finds a device
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                //Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //Add the name and address to an array adapter to show in the listview
                BTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                BTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == RESULT_OK){
                status.setText("Status: Enabled");
                Toast.makeText(MainActivity.this, "Bluetooth is turned on", Toast.LENGTH_LONG).show();
            }else{
                status.setText("Status: Disabled");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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
        Log.v("MainActivity", "onPause");
        if(myReceiver != null){
            try {
                unregisterReceiver(myReceiver);
            }catch(IllegalArgumentException e){
                Log.v("MainActivity", "try to unregister unregistered broadcast receiver");
            }
        }
        super.onPause();
    }
}
