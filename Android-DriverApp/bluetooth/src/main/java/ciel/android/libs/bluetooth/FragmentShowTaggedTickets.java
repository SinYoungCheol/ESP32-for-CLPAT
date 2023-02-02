package ciel.android.libs.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;

import ciel.android.libs.crypto.AesCtrCipher;
import ciel.android.libs.crypto.VigenereCipher;
import ciel.android.libs.crypto.Xcriptor;
import kotlin.text.Regex;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link FragmentShowTaggedTickets#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FragmentShowTaggedTickets extends Fragment implements ServiceConnection, SerialListener {

    private enum ConnectState { False, Pending, True }
    private static final String ARG_BLUETOOTH_DEVICE_MAC_ADDRESS = "bluetooth_device_mac_address";

    private SerialService mService;
    private String mBtMacAddress;
    private ArrayList<TaggedTicket> mItems = new ArrayList<>();
    private TaggedTicketRecyclerViewAdapter mAdapter;

    private ConnectState connected = ConnectState.False;
    private boolean initialStart = true;

    private Button btnAdd, btnClear;
    public FragmentShowTaggedTickets() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param btMacAddress Bluetooth's MAC Address.
     * @return A new instance of fragment FragmentShowTaggedTickets.
     */

    // TODO: Rename and change types and number of parameters
    public static FragmentShowTaggedTickets newInstance(String btMacAddress) {
        FragmentShowTaggedTickets fragment = new FragmentShowTaggedTickets();
        Bundle args = new Bundle();
        args.putString(ARG_BLUETOOTH_DEVICE_MAC_ADDRESS, btMacAddress);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mBtMacAddress = getArguments().getString(ARG_BLUETOOTH_DEVICE_MAC_ADDRESS);
        }

        /*
        * example items for DEBUG
        *
        mItems.clear();
        mItems.add(new TaggedTicket("01050407198", "2023-01-10 17:41:01"));
        mItems.add(new TaggedTicket("01031406195", "2023-01-10 17:41:01"));
        mItems.add(new TaggedTicket("01045452650", "2023-01-10 17:41:01"));

        mItems.get(1).setCertified(TaggedTicket.CertifiedResult.FormatError);
        */
        mAdapter = new TaggedTicketRecyclerViewAdapter(mItems);
    }

    @Override
    public void onDestroy() {
        if (connected != ConnectState.False) {
            disconnect();
        }
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mService != null) { // 이미 생성된 상태이면 attach
            mService.attach(this);
        }
        else {
            getActivity().startService(new Intent(getActivity(), SerialService.class));
        }
    }

    @Override
    public void onStop() {
        if (mService != null && getActivity().isChangingConfigurations()) {
            mService.detach();
        }
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(
                new Intent(getActivity(), SerialService.class),
                this,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        }
        catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 처음 시작할 때 false
        if (initialStart && mService != null) {
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_show_tagged_tickets, container, false);
        // Setup RecyclerView.Adapter
        RecyclerView taggedTicketRecyclerView = rootView.findViewById(R.id.tagged_ticket_recyclerview);
        taggedTicketRecyclerView.setHasFixedSize(true);
        taggedTicketRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        taggedTicketRecyclerView.setAdapter(mAdapter);
        return rootView;
    }

    private Random rnd;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnAdd = view.findViewById(R.id.btn_add);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 승차권 인증결과 만들기
                rnd = new Random();
                TaggedTicket.CertifiedResult[] certifiedValues  = TaggedTicket.CertifiedResult.values();
                int index = rnd.nextInt(6);
                TaggedTicket.CertifiedResult result = certifiedValues[index]; // 0에서 5사이의 값
                // 탑승자 휴대폰번호 만들기
                int phoneSeq = rnd.nextInt();
                StringBuilder phoneNo = new StringBuilder();
                phoneNo.append("010");
                phoneNo.append(String.format(Locale.KOREA, "%08d", Math.abs(phoneSeq)));
                // 승차권 태그 날짜/시간 만들기
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);
                String tagTime = sdf.format(new Date());
                // 항목 추가하기
                mItems.add(new TaggedTicket(phoneNo.substring(0, 11), tagTime, result));
                mAdapter.refresh();
            }
        });
        btnClear = view.findViewById(R.id.btn_clear);
        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mItems.clear();
                mAdapter.refresh();
            }
        });
    }

    private Timer timer;
    /**
    *  Local Methods
    */
    private void connect() {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice btDevice = btAdapter.getRemoteDevice(mBtMacAddress);
            // status("connecting ...");
            connected = ConnectState.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), btDevice);
            mService.connect(socket);

            /*
             *
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA);
                    String now = sdf.format(new Date());
                    send("[00,01050407198," + now + "]");
                }
            }, 1000, 3000);
            */
        }
        catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = ConnectState.False;
        mService.disconnect();
    }

    private void send(String msg) {
        if (connected != ConnectState.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] rxBytes = msg.getBytes();
            // TODO : (recyclerViewAdapter)에  항목추가
            // TODO : (bluetooth device)로 송신
            mService.write(rxBytes);
        }
        catch (Exception e) {
            onSerialIoError(e);
        }
    }

    Scenario scenario = new Scenario();
    private void  receive(ArrayDeque<byte[]> rxQueBytes) {

        for(byte[] rxBuff : rxQueBytes) {
            Log.v("receive", new String(rxBuff));
            if (rxBuff.length == 0)
                continue;

            String rxStr = (new String(rxBuff)).replace("\r\n", "");
            if (rxStr.length() == 0)
                continue;

            char c = rxStr.charAt(0);
            // TODO : 복호화 실시
            EncryptKinds encryptType; // initial value
            String encryptedText = null;
            if (c == '^' || c == '1') { // Encoded AES_CTR + peoples
                encryptType = EncryptKinds.AesCtrPeoples;
                encryptedText = new String(rxStr).substring(1);
            }
            else if (c == '2') { //
                encryptType = EncryptKinds.AesCtrSeat;
                encryptedText = new String(rxStr).substring(1);
            }
            else {
                encryptType = EncryptKinds.Vigenere;
                encryptedText = rxStr;
            }

            // TODO : [AES128-CTR]로 복호화
            String decryptedText = scenario.Verify(encryptType, encryptedText);
            if (decryptedText == null) {
                // TODO : notify to TTS (Invalid format)
                return;
            }
            else {
                // TODO : 공통 포맷 검사
                String[] tokens = decryptedText.split("\\|");
                // TODO : 탑승자 UID
                Regex regEx = new Regex("\\d+");
                if (!regEx.matches(tokens[0])) {
                    // TODO : notify to TTS
                    return;
                }
                // TODO : CTN
                regEx = new Regex("\\d+");
                if (!regEx.matches(tokens[1])) {
                    // TODO : notify to TTS
                    return;
                }
                // TODO : OTP value
                regEx = new Regex("\\d{6}");
                if (!regEx.matches(tokens[2])) {
                    // TODO : notify to TTS
                    return;
                }
                // TODO : collect person info
                /**
                 *                 person.UID = tokens[0];
                 *                 person.CTN = tokens[1];
                 *                 person.OTP = tokens[2];
                 *                 inspectOtp(person.OTP)
                 *                 ?? ESP32-WROOM-mini 가 가끔 불안정하다. 케이블 상태 확인요망
                 */
            }
        }
    }
    /**
    *   ServiceConnection 구현
    */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mService = ((SerialService.SerialBinder) iBinder).getService();
        mService.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mService = null;
    }

    /**
    *   SerialListener 구현
    */

    @Override
    public void onSerialConnect() {
        // status("connect");
        connected = ConnectState.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        // status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> rxData = new ArrayDeque<>();
        rxData.add(data);
        receive(rxData);
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> rxData) {
        receive(rxData);
    }

    @Override
    public void onSerialIoError(Exception e) {
        // status("connection lost: " + e.getMessage());
        disconnect();
    }

    public enum EncryptKinds { Vigenere, AesCtrPeoples, AesCtrSeat }
    public EncryptKinds encrypt;

    class Scenario {

        /**
         *
         * @param encryptKind : 열거형 상수값 (EncryptKinds) 중 하나. 버퍼에 수신된 첫번째 바이트 문자에 의해 정해진다.
         * @param encrypted_text : 복호화 대상 내용 (버퍼의 첫번째 바이트를 주의)
         * @return true or false
         */
        public String Verify(EncryptKinds encryptKind, String encrypted_text) {
            Xcriptor cipher;
            String decrypted_text = null;

            try {
                if (encryptKind == EncryptKinds.AesCtrSeat) {
                    cipher = new AesCtrCipher();
                    decrypted_text = cipher.Decrypt(encrypted_text);
                } else if (encryptKind == EncryptKinds.AesCtrPeoples) {
                    cipher = new AesCtrCipher();
                    decrypted_text = cipher.Decrypt(encrypted_text);
                } else if (encryptKind == EncryptKinds.Vigenere) {
                    cipher = new VigenereCipher();
                    decrypted_text = cipher.Decrypt(encrypted_text);
                }
                return decrypted_text;
            }
            catch (Exception e) {
                // TODO : notify to UI
                e.printStackTrace();
                return null;
            }
        }
    }
}