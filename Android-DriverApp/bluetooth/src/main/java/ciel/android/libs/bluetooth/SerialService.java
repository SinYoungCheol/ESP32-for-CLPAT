package ciel.android.libs.bluetooth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.os.Handler;

import java.io.IOException;
import java.util.ArrayDeque;

public class SerialService extends Service implements SerialListener {

    private Handler mMainLoopHandler; // = new Handler(Looper.getMainLooper());
    private SerialBinder mBinder;
    private SerialSocket mSocket;
    private SerialListener mListener;

    private ArrayDeque<QueueItem> queue1, queue2;
    private QueueItem lastRead = null;

    private boolean in_connected;

    // constructor
    public SerialService() {
        mMainLoopHandler = new Handler(Looper.getMainLooper());
        mBinder = new SerialBinder();
        queue1 = new ArrayDeque<>();
        queue2 = new ArrayDeque<>();
        lastRead = new QueueItem(QueueType.Read);
    }

    /*
        implements for Service
        -- 서비스의 라이프사이클 구현
    */

    @Override
    public void onDestroy() {
        cancelNotification();
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*
            implements for SerialListener
    */
    @Override
    public void onSerialConnect() {
        if(in_connected) {
            synchronized (this) {
                if (mListener != null) {
                    mMainLoopHandler.post(() -> {
                        if (mListener != null) {
                            mListener.onSerialConnect();
                        } else {
                            queue1.add(new QueueItem(QueueType.Connect));
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect));
                }
            }
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        if(in_connected) {
            synchronized (this) {
                if (mListener != null) {
                    mMainLoopHandler.post(() -> {
                        if (mListener != null) {
                            mListener.onSerialConnectError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.ConnectError, e));
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, e));
                    disconnect();
                }
            }
        }
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        throw new UnsupportedOperationException();
    }

    /**
     * <pre>
     * 데이터 청크를 병합하여 UI 업데이트 수를 줄입니다.
     * 데이터는 초당 100 청크에 도달할 수 있지만,
     * UI는 오직 receiveText 에 이미 많은 텍스트가 포함되어 있는 경우에만 12개의 업데이트를 수행합니다.
     *
     * 새 데이터의 경우 UI 스레드에 한 번 알립니다(1).
     * UI 스레드에 소비되지 않는 동안(2) 데이터를 더 추가합니다(3).
     * </pre>
     */
    @Override
    public void onSerialRead(byte[] data) {
        if(in_connected) {
            synchronized (this) {
                if (mListener != null) {
                    boolean first;
                    synchronized (lastRead) {
                        first = lastRead.byteArray.isEmpty(); // (1)
                        lastRead.add(data); // (3)
                    }
                    if(first) {
                        mMainLoopHandler.post(() -> {
                            ArrayDeque<byte[]> datas;
                            synchronized (lastRead) {
                                datas = lastRead.byteArray;
                                lastRead.init(); // (2)
                            }
                            if (mListener != null) {
                                mListener.onSerialRead(datas);
                            } else {
                                queue1.add(new QueueItem(QueueType.Read, datas));
                            }
                        });
                    }
                } else {
                    if(queue2.isEmpty() || queue2.getLast().type != QueueType.Read)
                        queue2.add(new QueueItem(QueueType.Read));
                    queue2.getLast().add(data);
                }
            }
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        if(in_connected) {
            synchronized (this) {
                if (mListener != null) {
                    mMainLoopHandler.post(() -> {
                        if (mListener != null) {
                            mListener.onSerialIoError(e);
                        } else {
                            queue1.add(new QueueItem(QueueType.IoError, e));
                            disconnect();
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, e));
                    disconnect();
                }
            }
        }
    }

    /*
        지역 합수
    */

    /**
     * ■ SerialSocket을 연결하기 위하여 Activity나 Fragment에서 아래의 샘플과 같이 호출
     * <pre>
     *     BluetoothDevice device = BluetoothAdapter
     *          .getDefaultAdapter()
     *          .getRemoteDevice(deviceAddress);
     *     ...
     *     SerialSocket socket = new SerialSocket(getActivity()
     *          .getApplicationContext(), device);
     *     service.connect(socket);
     * </pre>
     * @param socket 연결할 SerialSocket
     * @throws IOException try/catch blocks의 try block내 에서 호출
     */
    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        mSocket = socket;
        in_connected = true;
    }

    public void disconnect() {
        in_connected = false; // ignore data,errors while disconnecting
        cancelNotification();
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket = null;
        }
    }

    /**
     * ■ Activity 또는 Fragment에서 아래의 코드와 같이 호출
     * <pre>
     *  SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
     *  spn.setSpan(new ForegroundColorSpan(getResources()
     *      .getColor(
     *          R.color.colorSendText)),
     *          0,
     *          spn.length(),
     *          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
     *  receiveText.append(spn);
     *  service.write(data);
     *  </pre>
     * @param txBytes 전송할 바이트 배열
     * @throws IOException try/catch blocks의 try block내 에서 호출
     */
    public void write(byte[] txBytes) throws IOException {
        if (!in_connected) {
            throw new IOException("not connected");
        }
        mSocket.write(txBytes);
    }

    public void attach(SerialListener listener) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");

        cancelNotification();

        /*
          synchronized()를 사용하여 두번째 대기열에 새 항목을 추가하지 않도록한다.
          (원문) use synchronized() to prevent new items in queue2
          메인 스레드에서 mainLooper.post()와 attach()가 수행되므로 첫번째 대기열에 새 항목이 추가되지 않는다.
          자세한 설명은 현재 source file 의 내용 중 코드 블럭 'onSerialRead()'을 참조하라.
          (원문) new items will not be added to queue1 because mainLooper.post and attach() run in main thread
         */
        synchronized (this) {
            mListener = listener;
        }
        for(QueueItem item : queue1) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.byteArray); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        for(QueueItem item : queue2) {
            switch(item.type) {
                case Connect:       listener.onSerialConnect      (); break;
                case ConnectError:  listener.onSerialConnectError (item.e); break;
                case Read:          listener.onSerialRead         (item.byteArray); break;
                case IoError:       listener.onSerialIoError      (item.e); break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {
        if (in_connected) {
            createNotification();
            /*
             이벤트 대기열에 이미 있는 항목(main Looper()에서 분리되기 전에 게시됨)은 대기열 1에 있게 됩니다
             나중에 발생하는 항목은 queue2로 직접 이동됩니다. detach() 와 mainLooper.post()은
             메인 스레드에서 실행되므로 모든 항목이 잡힙니다
            */
        }
        mListener = null;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL,
                    "Background service",
                    NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }
        Intent disconnectIntent = new Intent()
                .setAction(Constants.INTENT_ACTION_DISCONNECT);
        Intent restartIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(
                this, 1, disconnectIntent, flags);
        PendingIntent restartPendingIntent = PendingIntent.getActivity(
                this, 1, restartIntent,  flags);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, Constants.NOTIFICATION_CHANNEL)
                .setSmallIcon(R.mipmap.ic_notification)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(mSocket != null ? "Connected to " + mSocket.getName() : "Background Service")
                .setContentIntent(restartPendingIntent)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.mipmap.ic_disconnect, "Disconnect", disconnectPendingIntent));
        // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
        // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        Notification notification = builder.build();
        startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
    }

    private void cancelNotification() {
        stopForeground(true);
    }


    public class SerialBinder extends Binder {
        SerialService getService() { return SerialService.this; }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private static class QueueItem {
        QueueType type;
        ArrayDeque<byte[]> byteArray;
        Exception e;

        // constructors
        QueueItem(QueueType type) { this.type = type; if(type == QueueType.Read) init(); }
        QueueItem(QueueType type, Exception e) { this.type = type; this.e = e; }
        QueueItem(QueueType type, ArrayDeque<byte[]> bytes) { this.type = type; this.byteArray = bytes; }

        void init() { byteArray = new ArrayDeque<>(); }
        void add(byte[] data) { byteArray.add(data); }
    }
}
