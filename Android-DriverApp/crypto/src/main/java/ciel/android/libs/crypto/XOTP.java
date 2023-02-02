package ciel.android.libs.crypto;

import java.util.Date;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class XOTP {

    public final static int TICKET_EXPIRE_SECONDS = 90; // 90 seconds
    public final static String HMAC_KEY = "hbtk";
    private final static String ALGORITHM = "HmacSHA256";
    private int mInterval;

    public XOTP(int interval) {
        mInterval = interval;
    }

    public XOTP() {
        this(TICKET_EXPIRE_SECONDS);
    }

    public long getUTC() {
        return (new Date()).getTime();
    }

    public String getCode() {
        long steps = getUTC() / mInterval;
        return getCodeFromTimeSpan(steps);
    }

    public String getCode(long utc) {
        long steps = utc / mInterval;
        return getCodeFromTimeSpan(steps);
    }

    public boolean verify(String otp_in, long utc) {
        String newOtp = getCode(utc - mInterval);
        // 주어진 <otp_in>의 90초 이전 구간인 경우
        if (otp_in.equals(newOtp))
            return true;

        // 주어진 <otp_in>의 90초 이후 구간인 경우
        newOtp = getCode(utc + mInterval);
        if (otp_in.equals(newOtp))
            return true;

        // 주어진 <otp_in>과 일치하면 (현재구간)
        newOtp = getCode(utc);
        // 일치하지 않으면 (false)를 반환
        return otp_in.equals(newOtp);
    }

    private String getCodeFromTimeSpan(long timeSpan) {
        // STEP 0, map the number of steps in a 8-bytes array (counter value)
        byte[] timeSpanBytes = new byte[8];
        timeSpanBytes[0] = 0x00;
        timeSpanBytes[1] = 0x00;
        timeSpanBytes[2] = 0x00;
        timeSpanBytes[3] = 0x00;
        timeSpanBytes[4] = (byte)((timeSpan >> 24) & 0xFF);
        timeSpanBytes[5] = (byte)((timeSpan >> 16) & 0xFF);
        timeSpanBytes[6] = (byte)((timeSpan >> 8) & 0XFF);
        timeSpanBytes[7] = (byte)((timeSpan & 0XFF));

        try {
            // STEP.1 get the HMAC-SHA256 hash from counter and key
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(HMAC_KEY.getBytes(), ALGORITHM));
            byte[] hash = mac.doFinal(timeSpanBytes);

            // STEP.2 apply dynamic truncation to obtain a 4-bytes string
            int offset = hash[hash.length - 1] & 0x0f; // TextUtil.toHexString(hash);
            int oneTimePassword = ((
                    (hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) |
                    (hash[offset + 3] & 0xff)) % 1000000;
            return String.format(Locale.getDefault(),
                    "06%d", oneTimePassword).substring(0, 6);
        }
        catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
