package com.jimengtec;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mSocket;
    BluetoothDevice mDevice;
    OutputStream    mOutputStream;
    InputStream     mInputStream;
    Thread          workerThread;

    private String TAG ="Dong";
    byte[]          readBuffer;
    int             readBufferPosition;
    //int             counter;
    volatile    boolean stopworker;
    TextView    textViewMessage;
    TextView    textViewDynamometerDisplay;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Button buttonConect = findViewById(R.id.buttonConect);
        Button buttonZero   = findViewById(R.id.buttonZero);
        textViewMessage = findViewById(R.id.textViewMessage);
        textViewDynamometerDisplay = findViewById(R.id.textViewDynamometerDisplay);


        //Connect Button
        buttonConect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               try
                {
                    findBT();
                    openBT();
                    sendData("aa 00 aa 0d");
                }
               catch (IOException  ex) {
                   textViewMessage.setText("蓝牙设置有误！");
               }
            }
        });

        // Send and close example. Not used at this moment.

        ////Send Button

        //sendButton.setOnClickListener(new View.OnClickListener()

        //{

        //public void onClick(View v)

        //{

        //try

        //{

        //sendData();

        //}

        //catch (IOException ex) { }

        //}

        //});

        //

        ////Close button

        //closeButton.setOnClickListener(new View.OnClickListener()

        //{

        //public void onClick(View v)

        //{

        //try

        //{

        //closeBT();

        //}

        //catch (IOException ex) { }

        //}

        //});

        //

    }

    void findBT(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            textViewMessage.setText("本机未发现蓝牙设备！");
        }

        if(!mBluetoothAdapter.isEnabled()){
            Intent  enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth,0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        textViewMessage.setText("设备未配置。");
        if(pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices){
                if (device.getName().equals("JDY-30")){   //JDY-30  WXCLJ-7 SPP-CA
                    mDevice = device;
                    textViewMessage.setText("配置列表中发现设备.");
                    break;
                }
            }
        }
    }

    void openBT() throws IOException{
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //JDY-16 / WXCLJ-7 //       UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");//JDY-30 0000ffe1-0000-1000-8000-00805f9b34fb
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        try{
            mSocket.connect();
        }catch (IOException e){
            e.printStackTrace();
        }


        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();
        beginListenForDate();

        textViewMessage.setText("设备链路已建立。");
    }

    void beginListenForDate(){
        final Handler handler = new Handler();
        final byte delimiter = 0x0d; //ASCII for new line;

        stopworker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()&& !stopworker){
                    try {
                        int bytesAvailable = mInputStream.available();
                        if (bytesAvailable>0){
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);
                            for (int i=0; i<bytesAvailable;i++){
                                byte b = packetBytes[i];
                                if (b == delimiter){
                                    byte[] encodeBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer,0,encodeBytes,0,encodeBytes.length);
//                                    final String data = new String(encodeBytes,"US-ASCII");
                                    StringBuilder sb = new StringBuilder("");
                                    String sTmp="";
                                    for (int n=0;n<encodeBytes.length;n++)
                                    {
                                        sTmp = Integer.toHexString(encodeBytes[n] & 0xFF);
                                        sb.append((sTmp.length()==1)? "0"+sTmp : sTmp);
                                        sb.append(" ");
                                    }
                                    final String data = sb.toString().toUpperCase().trim();;

                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                           textViewDynamometerDisplay.setText(data);
                                            Log.i(TAG,"data received:"+data);
                                        }
                                    });
                                }
                                else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex){
                        stopworker = true;
                    }
                }
            }
        });
        workerThread.start();

    }

    void sendData(String msg) throws  IOException{
        byte[] data = msg.getBytes();

        if (data == null) {
            return;
        }
        int nLength = data.length;

        String strTemString = new String(data, 0, nLength);
        String[] strings = strTemString.split(" ");
        nLength = strings.length;
        data = new byte[nLength];
        for (int i = 0; i < nLength; i++) {
            if (strings[i].length() != 2) {
                data[i] = 00;
                continue;
            }
            try {
                data[i] = (byte)Integer.parseInt(strings[i], 16);
            } catch (Exception e) {
                data[i] = 00;
                continue;
            }
        }

        //        msg += "\n";
        mOutputStream.write(data);
//        Log.i(TAG,"Data Sent");
    }
    void closeBT() throws IOException{
        stopworker = true;
        mOutputStream.close();
        mInputStream.close();
        mSocket.close();
        Log.i(TAG,"Bluetooth Closed");
    }

}
