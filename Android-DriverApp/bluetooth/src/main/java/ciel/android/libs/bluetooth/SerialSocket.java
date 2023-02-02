package ciel.android.libs.bluetooth;

import static java.util.Arrays.binarySearch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;

class SerialSocket implements Runnable {

    private static final UUID BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BroadcastReceiver mDisconnectBroadcastReceiver;
    private final Context mContext;
    private final BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private SerialListener mListener;
    private boolean in_connection = false;

    SerialSocket(Context context, BluetoothDevice device) {
        if (context instanceof Activity) {
            throw new InvalidParameterException("Context required, not UI");
        }

        mContext = context;
        mDevice = device;
        mDisconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mListener != null) { // 이미 등록된 리스너가 있으면
                    mListener.onSerialIoError(new IOException("The listener for disconnect is already running."));
                    Log.e("SerialSocket", "DISCONNECT");
                    disconnect();
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        try {
            mSocket = mDevice.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_SPP);
            mSocket.connect();
            if (mListener != null) {
                mListener.onSerialConnect();
            }
        } catch (Exception e) {
            if (mListener != null) {
                mListener.onSerialIoError(e);
            }
            // close()의 실패가 예상되지만 시도
            try {
                mSocket.close();
            } catch (Exception ignored) {
            } finally {
                mSocket = null;
            }
        }

        in_connection = true;
        byte[] buffer = new byte[1024];
        int rxSize = 0, rxPosition = 0;

        // 패킷의 마지막 (2 byte)는  '\r\n'
        try {
            while (in_connection) {
                if (mSocket.getInputStream() == null) {
                    continue;
                }

                if (mSocket.getInputStream().available() == 0) {
                    continue;
                }

                if (rxPosition >= buffer.length) { // 다음읽기 위치가 버퍼크기를 초과 ?
                    rxPosition = 0;
                    Arrays.fill(buffer, (byte)0);
                }

                rxSize = mSocket.getInputStream().read(buffer, rxPosition, buffer.length - rxPosition);
                if (rxSize == 0) { // 읽은 데이터가 없는 경우
                    Log.e("SerialSocket", "rxSize=" + rxSize);
                    continue;
                }


                String strBuff = (new String(buffer, 0, rxPosition + rxSize));
                String[] messages = strBuff.split("\n");
                for(String msg : messages) {
                    if (mListener != null) {
                        int cr_index = msg.lastIndexOf('\r');
                        if (cr_index != -1) {
                            mListener.onSerialRead(msg.substring(0, cr_index - 1).getBytes());
                        }
                    }
                }

                // STX 위치는 읽기의 시작위치
                int stx_index = 0;
                // ETX 탐색은 항상 버퍼의 rxPosition 위치부터 시작
                int etx_index = strBuff.indexOf('\r', stx_index);
                //int etx_index = Arrays.binarySearch(buffer, stx_index, rxSize, (byte)0x0d);
                if (etx_index == -1) { // CR (Carriage Return)이 없는 경우 다음 스트림을 읽기
                    rxPosition += rxSize;
                    continue;
                }

                // 패킷에는 하나 이상의 메시지가 있을 수 있다.
                while(etx_index != -1) {
                    int msg_size = etx_index - stx_index;
                    byte[] rxBytes = strBuff.substring(stx_index, etx_index).getBytes();
                    if (mListener != null) {
                        mListener.onSerialRead(rxBytes);
                    }

                    if (etx_index >= msg_size) {
                        etx_index = -1;
                        break;
                    }
                    // STX 위치는 이전 etx 위치에서 개행문자 길이만큼 합한 위치
                    stx_index = strBuff.substring(etx_index + 1, 1).equals("\n") ? etx_index + 2 : etx_index + 1;
                    // ETX 탐색은 항상 버퍼의 rxPosition 위치부터 시작
                    etx_index = strBuff.indexOf('\r', stx_index);
                }

                if (stx_index <= (rxPosition + rxSize) - 1) { // 잔여 파편이 남아있으면
                    int remainder_size = (rxPosition + rxSize) - stx_index;
                    byte[] remainder_bytes = Arrays.copyOfRange(buffer, stx_index, stx_index + remainder_size -1);
                    Arrays.fill(buffer, (byte)0);
                    buffer = Arrays.copyOfRange(remainder_bytes, 0, remainder_size);
                    Log.v("SerialSocket", "dump buffer[]=" + new String(buffer));
                    rxPosition = remainder_size;
                }
                else {
                    rxPosition = 0;
                    Arrays.fill(buffer, (byte)0);
                }
            }
        } catch (Exception e) {
            in_connection = false;
            if (mListener != null) {
                mListener.onSerialIoError(e);
            }
            try {
                mSocket.close();
            } catch (Exception ignored) {
            } finally {
                mSocket = null;
            }
        }
    }



    @SuppressLint("MissingPermission")
    public String getName() {
        if (mDevice != null) {
            if (mDevice.getName() != null) {
                return mDevice.getName();
            }
            else if (mDevice.getAddress() != null) {
                return mDevice.getAddress();
            }
        }
        return "(none)";
    }

    void connect(SerialListener listener) {
        mListener = listener;
        mContext.registerReceiver(mDisconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DISCONNECT));
        Executors.newSingleThreadExecutor().submit(this); // 자신이 runnable 이므로 작업을 예약
    }

    void disconnect() {
        mListener = null;
        in_connection = false;
        if (mSocket != null) {
            try {
                mSocket.close();
            }
            catch (Exception ignored) {
            }
            finally {
                mSocket = null;
            }
        }
        try {
            mContext.unregisterReceiver(mDisconnectBroadcastReceiver);
        }
        catch (Exception ignored) { }
    }

    void write(byte[] txBytes) throws IOException {
        if (!in_connection)
            throw new IOException("not connected");
        mSocket.getOutputStream().write(txBytes);
    }
}
