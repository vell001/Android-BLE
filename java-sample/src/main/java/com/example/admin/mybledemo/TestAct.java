package com.example.admin.mybledemo;

import static androidx.activity.result.ActivityResultCallerKt.registerForActivityResult;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.admin.mybledemo.ui.BleActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.BleLog;
import cn.com.heaton.blelibrary.ble.callback.BleConnectCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotifyCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;
import cn.com.heaton.blelibrary.ble.model.BleDevice;
import cn.com.heaton.blelibrary.ble.model.ScanRecord;
import cn.com.heaton.blelibrary.ble.utils.ByteUtils;

public class TestAct extends AppCompatActivity {

    public static final int REQUEST_GPS = 4;
    private static final String TAG = TestAct.class.getSimpleName();
    private Ble<BleRssiDevice> ble = Ble.getInstance();

    private List<BleRssiDevice> bleRssiDevices = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rescan();
    }

    private void rescan() {
        if (ble != null && !ble.isScanning()) {
            bleRssiDevices.clear();
            ble.startScan(scanCallback);
        }
    }

    //请求权限
    public void requestPermission() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            //根据实际需要申请定位权限
            permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> map) {
                // must all permissions agree
                checkBlueStatus();
            }
        }).launch(permissions.toArray(new String[0]));
    }

    //检查蓝牙是否支持及打开
    private void checkBlueStatus() {
        if (!ble.isSupportBle(this)) {
            com.example.admin.mybledemo.Utils.showToast(R.string.ble_not_supported);
            finish();
        }
        if (!ble.isBleEnable()) {
            Toast.makeText(this, "蓝牙未打卡", Toast.LENGTH_LONG).show();
        } else {
            checkGpsStatus();
        }
    }

    private void checkGpsStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !cn.com.heaton.blelibrary.ble.utils.Utils.isGpsOpen(TestAct.this)) {
            new AlertDialog.Builder(TestAct.this)
                    .setTitle("提示")
                    .setMessage("为了更精确的扫描到Bluetooth LE设备,请打开GPS定位")
                    .setPositiveButton("确定", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(intent, REQUEST_GPS);
                    })
                    .setNegativeButton("取消", null)
                    .create()
                    .show();
        } else {
            ble.startScan(scanCallback,Integer.MAX_VALUE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == Ble.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
        } else if (requestCode == Ble.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            ble.startScan(scanCallback,Integer.MAX_VALUE);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private BleScanCallback<BleRssiDevice> scanCallback = new BleScanCallback<BleRssiDevice>() {
        @Override
        public void onLeScan(final BleRssiDevice device, int rssi, byte[] scanRecord) {
            synchronized (ble.getLocker()) {
                for (int i = 0; i < bleRssiDevices.size(); i++) {
                    BleRssiDevice rssiDevice = bleRssiDevices.get(i);
                    if (TextUtils.equals(rssiDevice.getBleAddress(), device.getBleAddress())) {
                        if (rssiDevice.getRssi() != rssi && System.currentTimeMillis() - rssiDevice.getRssiUpdateTime() > 1000L) {
                            rssiDevice.setRssiUpdateTime(System.currentTimeMillis());
                            rssiDevice.setRssi(rssi);
                        }
                        return;
                    }
                }
//                if (device.getBleName() == null || !device.getBleName().startsWith("lingdong")) {
//                    return;
//                }
                if (device.getBleName() == null || !device.getBleName().startsWith("Joy")) {
                    return;
                }
                device.setScanRecord(ScanRecord.parseFromBytes(scanRecord));
                device.setRssi(rssi);
                bleRssiDevices.add(device);
                ble.connect(device, connectCallback);
                Log.i(TAG, "name: " + device.getBleName());
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            Log.e(TAG, "onStart" );
        }

        @Override
        public void onStop() {
            super.onStop();
            Log.e(TAG, "onStop " +ble.isScanning());
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    rescan();
                }
            },1000);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed: " + errorCode);
        }

    };

    private BleConnectCallback<BleRssiDevice> connectCallback = new BleConnectCallback<BleRssiDevice>() {
        @Override
        public void onConnectionChanged(BleRssiDevice device) {
            Log.e(TAG, device.getBleName() + " onConnectionChanged: " + device.getConnectionState() + " " + Thread.currentThread().getName());
        }

        @Override
        public void onConnectFailed(BleRssiDevice device, int errorCode) {
            super.onConnectFailed(device, errorCode);
            Utils.showToast("连接异常，异常状态码:" + errorCode);
        }

        @Override
        public void onConnectCancel(BleRssiDevice device) {
            super.onConnectCancel(device);
            Log.e(TAG, "onConnectCancel: " + device.getBleName());
        }

        @Override
        public void onServicesDiscovered(BleRssiDevice device, BluetoothGatt gatt) {
            super.onServicesDiscovered(device, gatt);
        }

        @Override
        public void onReady(BleRssiDevice device) {
            super.onReady(device);
            //连接成功后，设置通知
            ble.enableNotify((BleRssiDevice) device, true, new BleNotifyCallback<BleRssiDevice>() {
                @Override
                public void onChanged(BleRssiDevice device, BluetoothGattCharacteristic characteristic) {
                    UUID uuid = characteristic.getUuid();
                    BleLog.e(TAG, "onChanged==uuid:" + uuid.toString());
                    BleLog.e(TAG, "onChanged==data:" + ByteUtils.toHexString(characteristic.getValue()));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showToast(String.format("收到设备通知数据: %s", ByteUtils.toHexString(characteristic.getValue())));
                        }
                    });
                }

                @Override
                public void onNotifySuccess(BleRssiDevice device) {
                    super.onNotifySuccess(device);
                    BleLog.e(TAG, "onNotifySuccess: " + device.getBleName());
                }
            });

            Ble.getInstance().enableNotifyByUuid(device, true, serviceUUID, characteristicUUID, new BleNotifyCallback<BleDevice>() {
                @Override
                public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
                    TestAct.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(TestAct.this, String.format("按键事件: %s%s", "(0x)", ByteUtils.bytes2HexStr(characteristic.getValue())), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        }
    };
    private UUID serviceUUID = UUID.fromString("0000FEE7-0000-1000-8000-00805f9b34fb");
    private UUID characteristicUUID = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb");
}
