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

import java.util.Arrays;
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
    Boolean     connected = false;
    TextView    textViewMessage;
    TextView    textViewDynamometerDisplay;
    Button      buttonConect;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        buttonConect = findViewById(R.id.buttonConect);
        Button buttonZero   = findViewById(R.id.buttonZero);

        textViewDynamometerDisplay = findViewById(R.id.textViewDynamometerDisplay);


        //Connect Button
        buttonConect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connected)return;
                buttonConect.setText("正在连接");
                try
                {
                    findBT();
                    openBT();
//                    sendData("a5 80 07 2c 5a");
                    sendData("aa 87 31 0d");
                }
               catch (IOException  ex) {
                   Log.i(TAG,"力值读取指令发送异常！");
               }
            }
        });

        //Connect Button
        buttonZero.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!connected) return;
                try {
                    sendData("aa c7 71 0d");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.i(TAG,"清零指令发送异常！");
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                try {
                    sendData("aa 87 31 0d");
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });

    }

    void findBT(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Log.i(TAG,"设备未发现蓝牙适配器。");
        }

        if(!mBluetoothAdapter.isEnabled()){
            Intent  enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth,0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
//        textViewMessage.setText("设备未配置。");
        if(pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices){
                if (device.getName().equals("JMCLJ")){   //JDY-30  WXCLJ-7 SPP-CA
                    mDevice = device;
//                    textViewMessage.setText("配置列表中发现设备.");
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
            connected = true;
        }catch (IOException e){
            connected = false;
            e.printStackTrace();
        }


        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();
        beginListenForDate();
        buttonConect.setText("连接成功");
//        textViewMessage.setText("设备链路已建立。");
    }

    void beginListenForDate(){
        final Handler handler = new Handler();
        final byte delimiter = 0x0d; //ASCII for new line is 10

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
                                    readBufferPosition = 0;
                                    final String data = GetdynamoValue(encodeBytes);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (data.equals("")){
                                                Log.i(TAG,"data received:"+data);
                                            }else{
                                                textViewDynamometerDisplay.setText(data);
                                                Log.i(TAG,"data received:"+data);
                                            }
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

    String GetdynamoValue( byte[] arr){
        boolean negetive = false;
        float floatValue;
        String StrDynamoValue;
        float dotWeight;
        if(arr.length==5){
                byte[] TemValue = Arrays.copyOfRange(arr,1,4);
                byte dotPosition =arr[4];
//                ByteBuffer bbTem = ByteBuffer.wrap(TemValue);
//                bbTem.order(ByteOrder.LITTLE_ENDIAN);
//                int intValue = bbTem.getInt();

            int intValue = (TemValue[0]&0xFF)*65536+(TemValue[1]&0xFF)*256+(TemValue[2]&0xFF);
            if (intValue >= 0x800000){
                if(intValue == 0x800000){
                   negetive = false;
                }else{
                    negetive = true;
                }
                    intValue = intValue-0x800000;
                }
                dotWeight = (float)Math.pow(10, dotPosition&0xFF);
                floatValue = (float)intValue/dotWeight;
        }else{
            return "";
        }
        if (negetive){
            StrDynamoValue ="-"+ Float.toString(floatValue);
        }else {
            StrDynamoValue =Float.toString(floatValue);
        }
        if(dotWeight == 1.0){
            StrDynamoValue = StrDynamoValue.replace(".0","");
        }
        return StrDynamoValue+" N";
    }

    void sendData(String msg) throws  IOException{
        byte[] data = msg.getBytes();
        int nLength = data.length;

        if (nLength <= 0) {
            return;
        }

        String strTemString = new String(data, 0, nLength);
        String[] strings = strTemString.split(" ");
        nLength = strings.length;
        data = new byte[nLength];
        for (int i = 0; i < nLength; i++) {
            if (strings[i].length() != 2) {
                data[i] = 0b00;
                continue;
            }
            try {
                data[i] = (byte)Integer.parseInt(strings[i], 16);
            } catch (Exception e) {
                data[i] = 0x00;
//                continue;
            }
        }

        //        msg += "\n";
        mOutputStream.write(data);
        Log.i(TAG,"Data Sent");
    }
    void closeBT() throws IOException{
        stopworker = true;
        connected = false;
        mOutputStream.close();
        mInputStream.close();
        mSocket.close();
        Log.i(TAG,"Bluetooth Closed");
    }

}
