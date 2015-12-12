package com.witworks.witworksmanager;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bt_adapter = null;
    private ListView bt_list_view;

    private ArrayList<BluetoothDevice> bt_device_list = null;
    private BluetoothSocket bt_socket = null; // object of BluetoothSocket or BluetoothServerSocket

    private Thread bluetooth_server_thread = null;

    private boolean bt_scanning = false;
    private boolean is_watch = false;
    private boolean bt_connected = false;

    final UUID witworks_uuid = UUID.fromString("237df75e-685e-4f7c-9c02-d3d8735e73b3");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_device_list = new ArrayList<>();

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
                BluetoothDevice device = bt_device_list.get(position);

                if (bt_connected) {
                    try {
                        bt_socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    if (is_watch) {
                        // Setup bluetooth client
                        try {
                            bt_socket = device.createRfcommSocketToServiceRecord(witworks_uuid);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(), "Creating Bluetooth socket failed", Toast.LENGTH_LONG).show();
                        }

                        try {
                            bt_socket.connect();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(), "Connection failed", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // Setup bluetooth master
                        BluetoothServerSocket bt_server_socket = null;
                        try {
                            bt_server_socket = bt_adapter.listenUsingRfcommWithServiceRecord("WitworksManager", witworks_uuid);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getApplicationContext(),
                                    "Could not get a bluetoothserversocket: " + e.toString(),
                                    Toast.LENGTH_LONG).show();
                        }

                        final BluetoothServerSocket finalBt_server_socket = bt_server_socket;
                        bluetooth_server_thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while (true) {
                                    try {
                                        assert finalBt_server_socket != null;
                                        bt_socket = finalBt_server_socket.accept();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        break;
                                    }

                                    if (bt_socket != null) {
                                        try {
                                            finalBt_server_socket.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }
                            }
                        });
                        bluetooth_server_thread.start();
                    }
                    bt_connected = true;
                }
            }
        });
    }

    public void on_bt_scan_button_clicked(View v) {
        Button scan_button = (Button) v;

        if (bt_scanning) {
            bt_adapter.cancelDiscovery();
            scan_button.setText("Scan Witworks App");
            bt_scanning = false;
        } else {
            if (!bt_adapter.isEnabled()) {
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
                /*
                 * TODO: From the MAC address generate a unique string and show it to user
                 * Same string must be shown in phone and device's witworks app.
                 */
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
