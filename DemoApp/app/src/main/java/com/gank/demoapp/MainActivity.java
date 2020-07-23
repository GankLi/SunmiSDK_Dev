package com.gank.demoapp;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sunmi.scanner.SunmiScannerUtils;

import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    public static final String ACTION_USB_PERMISSION = "com.gank.demoapp.ACTION_USB_PERMISSION";

    EditText mEdit;
    TextView mText;
    ImageView mImage;

    UsbManager mUsbManager;

    UsbInterface mUsbInterface;
    UsbDeviceConnection mConnection;
    UsbEndpoint mIn, mOut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEdit = findViewById(R.id.edit);
        mText = findViewById(R.id.text);
        mImage = findViewById(R.id.image);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        findViewById(R.id.btn1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                posInterruptRead();
            }
        });

        mEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && count == 1 && s.charAt(start) == 10) {
                    posInterruptRead();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        IntentFilter filter = new IntentFilter();
        //注册USB设备插入，拔出动作
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        UsbDevice targetDevice = null;
        HashMap<String, UsbDevice> deviceMap = mUsbManager.getDeviceList();
        Iterator<UsbDevice> iterator = deviceMap.values().iterator();
        while (iterator.hasNext()) {
            UsbDevice device = iterator.next();
            if (SunmiScannerUtils.isSunmiScanner(device)) {
                targetDevice = device;
                if (!mUsbManager.hasPermission(targetDevice)) {
                    IntentFilter filterUsb = new IntentFilter(ACTION_USB_PERMISSION);
                    registerReceiver(mUsbPermissionReceiver, filterUsb);
                    PendingIntent pi = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                    mUsbManager.requestPermission(targetDevice, pi);
                } else {
                    usbDeviceInit(targetDevice);
                }
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
    }

    private void posInterruptRead() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mIn != null && mOut != null && mConnection != null) {
                    final byte[] buffer = SunmiScannerUtils.readScannerImage(mConnection, mIn, mOut);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BitmapFactory.Options opt = new BitmapFactory.Options();
                            opt.inJustDecodeBounds = true;
                            Bitmap bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, opt);
                            Log.e("Gank", "1 Decode -- bitmap: " + bitmap + " H: " + opt.outHeight + " W: " + opt.outWidth);
                            opt.inJustDecodeBounds = false;
                            bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
                            mImage.setImageBitmap(bitmap);
                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(opt.outWidth, opt.outHeight);
                            mImage.setLayoutParams(params);
                        }
                    });
                }
            }
        }).start();
    }

    private void usbDeviceInit(UsbDevice device) {
        Log.v("Gank", "-------- usbDeviceInit ----------");
        final int interfaceCount = device.getInterfaceCount();
        for (int i = 0; i < interfaceCount; i++) {
            mUsbInterface = device.getInterface(i);
            if (mUsbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_HID && mUsbInterface.getInterfaceSubclass() == 0) {
                break;
            }
        }
        Log.v("Gank", "Inter: " + mUsbInterface);
        if (mUsbInterface != null) {
            //获取UsbDeviceConnection
            mConnection = mUsbManager.openDevice(device);
            Log.v("Gank", "Con: " + mConnection);
            if (mConnection != null) {
                if (mConnection.claimInterface(mUsbInterface, true)) {
                    for (int j = 0; j < mUsbInterface.getEndpointCount(); j++) {
                        UsbEndpoint endpoint = mUsbInterface.getEndpoint(j);
                        Log.v("Gank", "Type: " + endpoint.getType());
                        //类型为大块传输
                        if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                            if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                                mOut = endpoint;
                            } else {
                                mIn = endpoint;
                            }
                        }
                    }
                }
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mConnection != null && mOut != null && mIn != null){
                    mText.setText("扫码枪： " + mUsbInterface.getName() + " 已经就绪");
                } else {
                    mText.setText("扫码抢未插入");
                }
            }
        });
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (SunmiScannerUtils.isSunmiScanner(device)) {
                mConnection = null;
                mOut = null;
                mIn = null;
                mUsbInterface = null;
                usbDeviceInit(device);
            }
        }
    };

    private BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.v("Gank", "Device - " + intent.getAction());
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted) {
                    //获得了usb使用权限
                    usbDeviceInit(device);
                }
            }
        }
    };

}