package ciel.android.libs.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kotlin.text.Charsets;
import ciel.android.libs.TextUtils;

public class AesCtrCipher extends Xcriptor {
    private static int ticketExpireSec = 90;
    private static String secretKey = "hbtkhbtkhbtkhbtk"; // { 'h', 'b', 't', 'k', 'h', 'b', 't', 'k', 'h', 'b', 't', 'k', 'h', 'b', 't', 'k' }
    private static byte[] ivBytes = new byte[] {
            (byte)0xf0, (byte)0xf1, (byte)0xf2, (byte)0xf3,
            (byte)0xf4, (byte)0xf5, (byte)0xf6, (byte)0xf7,
            (byte)0xf8, (byte)0xf9, (byte)0xfa, (byte)0xfb,
            (byte)0xfc, (byte)0xfd, (byte)0xfe, (byte)0xff
    };

    @Override
    public String Encrypt(String plain_in) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String Decrypt(String cipher_in) throws RuntimeException {
        // 평문이 [HexString]으로 구성되어있다.
        byte[] textBytes = TextUtils.getBytesFromHexString(cipher_in);

        AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        SecretKeySpec newKey = new SecretKeySpec(secretKey.getBytes(), "AES");
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            try {
                cipher.init(Cipher.DECRYPT_MODE, newKey, ivSpec);
                return new String(cipher.doFinal(textBytes), Charsets.UTF_8);
            }
            catch (Exception e) {
                e.printStackTrace();
                return null;
            }

        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
