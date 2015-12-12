package com.witworks.witworksmanager;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Set;

import javax.security.auth.callback.Callback;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bt_adapter = null;
    private ListView bt_list_view;
    private ArrayList<BluetoothDevice> bt_device_list = null;
    private boolean bt_scanning = false;
    private boolean is_watch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_device_list = new ArrayList<BluetoothDevice>();

        // Check for bluetooth device, exit if not present
        if (bt_adapter == null) {
            bt_adapter = BluetoothAdapter.getDefaultAdapter();
            if (bt_adapter == null) {
                Log.i("WitworksManager", "No bluetooth adapter present in the device");
                new AlertDialog.Builder(this)
                        .setTitle("Not compatible")
                        .setMessage("No bluetooth adapter present")
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                System.exit(0);
                            }
                        })
                    .show();
            }
        }

        bt_list_view = (ListView) findViewById(R.id.bt_dev_list);
        bt_list_view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListView lv = (ListView) parent;
                BluetoothDevice device = (BluetoothDevice) bt_device_list.get(position);
                Object dev = lv.getItemAtPosition(position);

                if (is_watch == true) {
                    // Setup bluetooth client
                } else {
                    // Setup bluetooth master
                }
            }
        });
    }

    public void on_bt_scan_button_clicked(View v) {
        Button scan_button = (Button) v;

        if (bt_scanning == true) {
            bt_adapter.cancelDiscovery();
            scan_button.setText("Scan Witworks App");
            bt_scanning = false;
        } else {
            if (bt_adapter.isEnabled() == false) {
                Intent bt_on = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(bt_on, 0);
                // Toast.makeText(getApplicationContext(), "Turned bluetooth on", Toast.LENGTH_LONG).show();
            }

            Intent visible = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(visible, 0);

            Set<BluetoothDevice> paired = bt_adapter.getBondedDevices();
            final ArrayList list = new ArrayList<BluetoothDevice>();

            for (BluetoothDevice bt_dev : paired) {
                bt_device_list.add(bt_dev);
                // TODO: From the MAC address generate a unique number / string and show it to user
                list.add(bt_dev.getName());
            }

            final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
            bt_list_view.setAdapter(adapter);

            IntentFilter bt_filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            BroadcastReceiver bt_receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        bt_device_list.add(device);
                        list.add(device.getName());
                        bt_list_view.setAdapter(adapter);
                    }
                }
            };

            getApplicationContext().registerReceiver(bt_receiver, bt_filter);
            bt_adapter.startDiscovery();

            bt_scanning = true;
            scan_button.setText("Stop scanning");
        }
    }
}
