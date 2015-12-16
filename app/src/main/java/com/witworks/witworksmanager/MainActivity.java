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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    final int BT_ACTION_REQUEST_ENABLE = 1;
    final int BT_ACTION_REQUEST_DISCOVERABLE = 2;

    private BluetoothAdapter bt_adapter = null;
    private ListView bt_list_view = null;
    private BluetoothDevice bt_to_pair = null;

    private ArrayList<BluetoothDevice> bt_device_list = null;
    private BluetoothSocket bt_socket = null; // object of BluetoothSocket or BluetoothServerSocket

    private boolean bt_scanning = false;
    private boolean bt_discoverable = false;
    private boolean bt_connected = false;
    private boolean bt_is_paired = false;
    private boolean is_server = false;

    private BroadcastReceiver bt_info_receiver = null;

    private Thread bt_reader_thread = null;

    private String bt_paired_dev_address = "";

    // final UUID witworks_uuid = UUID.fromString("237df75e-685e-4f7c-9c02-d3d8735e73b3");
    final UUID witworks_uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

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

        File settings_file = new File(getApplicationContext().getFilesDir(), "witworksmanager_settings");
        try {
            FileReader settings_reader = new FileReader(settings_file);
            char[] address = new char[16];
            int address_len = settings_reader.read(address, 0, 8);
            if (address_len > 0)
                bt_paired_dev_address = address.toString();
        } catch (FileNotFoundException e) {
            // Settings file is not available. We are pairing for the first time. Let's proceed.
            Log.d("WitworksManager", "Settings file is not available. We are pairing for the first time. Let's proceed.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!bt_adapter.isEnabled()) {
            Intent enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enable_intent, BT_ACTION_REQUEST_ENABLE);
        } else {
            bt_request_discoverable();
        }

        final String ACTION_DISAPPEARED = "android.bluetooth.device.action.DISAPPEARED";
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);

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
                        Toast.makeText(getApplicationContext(), "Bluetooth turned off, enabling", Toast.LENGTH_LONG).show();
                        Intent enable_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enable_intent, BT_ACTION_REQUEST_ENABLE);
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
                } else {
                    if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                        final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                        final int prev_state = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BluetoothDevice.ERROR);
                        if (state == BluetoothDevice.BOND_BONDED) {
                            bt_is_paired = true;
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            Toast.makeText(getApplicationContext(), "Paired with " + device.getName(), Toast.LENGTH_LONG).show();
                            File settings_file = new File(getApplicationContext().getFilesDir(), "witworksmanager_settings");
                            if (!settings_file.exists()) {
                                // Settings file not available, this is the first time pairing. Let's store the device address.
                                try {
                                    settings_file.createNewFile();
                                    FileWriter settings_writer = new FileWriter(settings_file);
                                    settings_writer.write(device.getAddress());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            // TODO: Create thread for receiving message
                            // The device that initiates the pairing will be the server
                            if (is_server) {
                                try {
                                    final BluetoothServerSocket srv_sock = bt_adapter.listenUsingRfcommWithServiceRecord("Witworks Server", witworks_uuid);
                                    Thread bt_server_listener = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                bt_socket = srv_sock.accept();
                                                if (bt_socket != null) {
                                                    srv_sock.close();
                                                    bt_reader_thread = start_reader_thread();
                                                    bt_reader_thread.start();
                                                    return;
                                                }
                                            } catch (IOException e) {
                                                // e.printStackTrace();
                                                return;
                                            }
                                        }
                                    });
                                    bt_server_listener.start();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                try {
                                    bt_socket = device.createRfcommSocketToServiceRecord(witworks_uuid);
                                } catch (IOException e) {
                                    // TODO: Print error message whenever possible
                                    e.printStackTrace();
                                }
                            }
                        } else if (state == BluetoothDevice.BOND_NONE && prev_state == BluetoothDevice.BOND_BONDED) {
                            if (bt_socket != null) {
                                try {
                                    bt_socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
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

    public void on_bt_send_button_clicked(View v) {
        EditText et = (EditText) findViewById(R.id.text_to_send);
        try {
            OutputStream output_stream = bt_socket.getOutputStream();
            String data = null;
            if (et.getText().length() > 0) {
                data = et.getText().toString();
                output_stream.write(data.getBytes(), 0, data.length());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public void on_bt_scan_button_clicked(View v) {
        if (bt_scanning) {
            bt_adapter.cancelDiscovery();
        } else {
            if (bt_discoverable) {
                bt_request_discoverable();
            }
            bt_adapter.startDiscovery();
        }
    }

    private void bt_request_discoverable() {
        Intent make_discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        startActivityForResult(make_discoverable, BT_ACTION_REQUEST_DISCOVERABLE);
    }

    private void pair_device(BluetoothDevice device) {
        if (bt_paired_dev_address.length() != 0) {
            if (bt_paired_dev_address == device.getAddress()) {
                device.createBond();
                is_server = true;
            } else {
                // TODO: Not pairing this device, notify this to the user
            }
        } else {
            device.createBond();
            is_server = true;
        }
    }

    private Thread start_reader_thread() {
        Thread reader_thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[64];
                try {
                    InputStream input_stream = bt_socket.getInputStream();
                    while (true) {
                        try {
                            input_stream.read(buffer);
                            Toast.makeText(getApplicationContext(), buffer.toString(),
                                    Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        });

        return reader_thread;
    }

    @Override
    protected void onActivityResult(int request_code, int result, Intent data) {
        if (request_code == BT_ACTION_REQUEST_ENABLE) {
            bt_request_discoverable();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bt_adapter.isDiscovering()) {
            bt_adapter.cancelDiscovery();
        }

        if (bt_socket != null) {
            try {
                bt_socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (bt_reader_thread != null) {
            try {
                bt_reader_thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (bt_device_list != null)
            bt_device_list.clear();

        unregisterReceiver(bt_info_receiver);
    }
}
