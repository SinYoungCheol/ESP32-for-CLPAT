package ciel.android.libs.crypto;

public abstract class Xcriptor {
    public String Encrypt(String plain_in)
    {
        return null;
    }

    public String Decrypt(String cipher_in) throws RuntimeException {
        return null;
    }

    public boolean VerifyOTP(String otp_in) {
        XOTP xotp = new XOTP(XOTP.TICKET_EXPIRE_SECONDS);
        boolean ret = xotp.verify(otp_in, xotp.getUTC());
        xotp = null;
        return ret;
    }
}
