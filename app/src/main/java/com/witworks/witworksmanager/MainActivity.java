package com.witworks.witworksmanager;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bt_adapter = null;
    private ListView bt_list_view;
    private BluetoothDevice bt_to_pair = null;

    private ArrayList<BluetoothDevice> bt_device_list = null;
    private BluetoothSocket bt_socket = null; // object of BluetoothSocket or BluetoothServerSocket

    private boolean bt_scanning = false;
    private boolean bt_discoverable = false;
    private boolean bt_connected = false;
    private boolean bt_is_paired = false;

    private BroadcastReceiver bt_info_receiver = null;

    final UUID witworks_uuid = UUID.fromString("237df75e-685e-4f7c-9c02-d3d8735e73b3");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bt_device_list = new ArrayList<>();
        ArrayList list = new ArrayList<>();
        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        bt_list_view = (ListView) findViewById(R.id.bt_dev_list);
        bt_list_view.setAdapter(adapter);

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

        if (!bt_adapter.isEnabled()) {
            Intent enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enable_intent, 1);
        } else {
            Intent discoverable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(discoverable_intent, 2);
        }

        final String ACTION_DISAPPEARED = "android.bluetooth.device.action.DISAPPEARED";
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        // filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

        // filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(ACTION_DISAPPEARED);

        bt_info_receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Toast.makeText(getApplicationContext(), "Bluetooth turned off", Toast.LENGTH_LONG).show();
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_LONG).show();
                    }
                } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        Toast.makeText(getApplicationContext(), "Bluetooth is discoverable", Toast.LENGTH_LONG).show();
                        bt_discoverable = true;
                    } else if (state == BluetoothAdapter.SCAN_MODE_CONNECTABLE || state == BluetoothAdapter.SCAN_MODE_NONE)
                        bt_discoverable = false;
                } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                    bt_scanning = true;
                    Button scan_button = (Button) findViewById(R.id.bt_scan_button);
                    scan_button.setText("Stop scanning");
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    bt_scanning = false;
                    Button scan_button = (Button) findViewById(R.id.bt_scan_button);
                    scan_button.setText("Scan Witworks app");

                    if (bt_to_pair != null) {
                        pair_device(bt_to_pair);
                        bt_to_pair = null;
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    final int prev_state = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                            BluetoothDevice.ERROR);
                    if (state == BluetoothDevice.BOND_BONDED) {
                        bt_is_paired = true;
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        Toast.makeText(getApplicationContext(), "Paired with " + device.getName(), Toast.LENGTH_LONG).show();
                    } else if (state == BluetoothDevice.BOND_NONE && prev_state == BluetoothDevice.BOND_BONDED) {
                        bt_is_paired = false;
                        Toast.makeText(getApplicationContext(), "Unpaired", Toast.LENGTH_LONG).show();
                    }
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (!bt_device_list.contains(device)) {
                        bt_device_list.add(device);
                        ArrayAdapter adapter = (ArrayAdapter) bt_list_view.getAdapter();
                        /*
                         * TODO: From the MAC address generate a unique string and show it to user
                         * Same string must be shown in phone and device's witworks app.
                         */
                        adapter.add(device.getName());
                        bt_list_view.setAdapter(adapter);
                    }
                } else if (ACTION_DISAPPEARED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    bt_device_list.remove(device);
                    ArrayAdapter adapter = (ArrayAdapter) bt_list_view.getAdapter();
                    adapter.remove(device.getName());
                    bt_list_view.setAdapter(adapter);
                } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                    Toast.makeText(getApplicationContext(), "Pairing in progress", Toast.LENGTH_LONG).show();
                }
            }
        };
        registerReceiver(bt_info_receiver, filter);

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
                    if (bt_adapter.isDiscovering()) {
                        bt_adapter.cancelDiscovery();
                        bt_to_pair = device;
                    } else {
                        pair_device(device);
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bt_adapter.cancelDiscovery();
        unregisterReceiver(bt_info_receiver);
    }

/*
    private void bt_enabled() {
        ArrayList list = new ArrayList<>();
        Set<BluetoothDevice> paired = bt_adapter.getBondedDevices();

        for (BluetoothDevice bt_dev : paired) {
            // bt_device_list.add(bt_dev);
            // list.add(bt_dev.getName());
        }

        final ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, list);
        bt_list_view.setAdapter(adapter);
    }
*/

    public void on_bt_scan_button_clicked(View v) {
        if (bt_scanning) {
            bt_adapter.cancelDiscovery();
        } else {
            if (bt_discoverable) {
                Intent discoverable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivityForResult(discoverable_intent, 2);
            }
            bt_adapter.startDiscovery();
        }
    }

    @Override
    protected void onActivityResult(int request_code, int result, Intent data) {
        // super.onActivityResult(request_code, result, data);
        if (request_code == 1) {
            Intent discoverable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(discoverable_intent, 2);
        }
    }

    private void pair_device(BluetoothDevice device) {
        String dev_name = device.getName();
        dev_name = dev_name;
        device.createBond();
        /*
        Intent pairing_intent = new Intent(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairing_intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        pairing_intent.putExtra(BluetoothDevice.EXTRA_PAIRING_KEY,
                (int) Math.floor(Math.random() * 100000));
        pairing_intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION);
        // pairing_intent.putExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.PAIRING_VARIANT_PIN);
        pairing_intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(pairing_intent, 3);
        // getApplicationContext().startActivityForResult(pairing_intent);
        // getApplicationContext().sendOrderedBroadcast(intent, getApplicationContext().);
        */
    }
}
