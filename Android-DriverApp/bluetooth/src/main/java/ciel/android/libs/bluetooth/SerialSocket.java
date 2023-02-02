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
import java.io.InputStream;
import java.nio.Buffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        byte[] buffer = new byte[256];
        int rxSize = 0, rxPosition = 0;

        // 가정 : 버퍼에는 하나의 패킷에 다수의 메시지가 있다.
        // 하나의 메시지의 끝은 '\r', '\n' 으로 식별한다.
        try {
            // int debug_path = 1;
            while (in_connection) {
                if (mSocket.getInputStream() == null) {
                    continue;
                }

                if (mSocket.getInputStream().available() == 0) {
                    continue;
                }

                if (rxPosition >= buffer.length) { // 다음읽기 위치가 버퍼 크기를 초과 ?
                    rxPosition = 0;
                    Arrays.fill(buffer, (byte)0);
                }

                rxSize = mSocket.getInputStream().read(buffer, rxPosition, buffer.length - rxPosition);
              /* 패킷의 파편화 처리에 대한 디버깅용 코드 조각
              *  debug_path = 1 : 1차 시도 (후미에 ETX 가 없는 패킷을 버퍼에 강제 할당)
              *  debug_path = 2 : 2차 시도 (1차 시도의 나머지 패킷을 강제 할당), 이때 debug_path 는 강제로 2로 할당하여 흐름을 변경해야 한다.
              *
                if (debug_path == 1) {
                    rxPosition = 0;
                    String sample = "12345\r\n67890\r\nABCDE";
                    byte[] tmp = Arrays.copyOfRange(sample.getBytes(), 0, sample.length());
                    Arrays.fill(buffer, (byte)0);
                    for(int k=0;k<tmp.length;k++) { buffer[k] = tmp[k];}
                    rxSize = tmp.length;
                }
                else if (debug_path == 2) {
                    String sample = "FGH\r\n";
                    byte[] tmp = Arrays.copyOfRange(sample.getBytes(), 0, sample.length());
                    for(int k=0;k<tmp.length;k++) { buffer[rxPosition + k] = tmp[k];}
                    rxSize = tmp.length;
                }
             */
                if (rxSize == 0) { // 읽은 데이터가 없는 경우
                    Log.e("SerialSocket", "rxSize=" + rxSize);
                    continue;
                }

                // 실제 수신한 데이터만을 복사
                String strBuff = new String(buffer, 0, rxPosition + rxSize);
                // (ETX)가 탐색되지 않으면 다음 패킷을 읽는다.
                if (strBuff.indexOf('\r') == -1) {
                    rxPosition += rxSize;
                    continue;
                }

                // 하나 이상의 메시지가 있을 수 있으므로 분할하여 각 메시지별로 처리
                String[] messages = strBuff.split("\\n");
                int etx_index = -1;
                for(String msg : messages) {
                    etx_index = msg.lastIndexOf('\r');
                    if (etx_index == -1) { // 메시지 중 (ETX)가 없는 경우
                        // 처리하지 못한 잔여 메시지를 버퍼로 옮긴다.
                        // 1. 버퍼를 클리어
                        Arrays.fill(buffer, (byte)0);
                        // 2. 잔여 메시지를 버퍼의 선두에 배치 
                        byte[] remainder = msg.getBytes();
                        System.arraycopy(remainder, 0, buffer, 0, remainder.length);
                        // 3. 수신위치를 잔여 메시지의 끝에 위치시킴
                        rxPosition = remainder.length;
                        break;
                    }

                    msg = msg.substring(0, etx_index);
                    if (mListener != null) {
                        mListener.onSerialRead(msg.getBytes());
                    }
                }
                if (etx_index != -1) {
                    // 패킷내의 모든 메시지를 처리한 경우
                    rxPosition = 0;
                    Arrays.fill(buffer, (byte) 0);
                } // ETX 탐색 실패이면 다음 패킷 읽기
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
