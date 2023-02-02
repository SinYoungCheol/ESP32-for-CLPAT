package ciel.android.libs.crypto;

import java.util.ArrayList;
import java.util.Arrays;

public class VigenereCipher extends Xcriptor {
    public final static int MAX_SOURCE_LENGTH = 36;

    @Override
    public String Encrypt(String plain_in) {
        XOTP xotp = new XOTP(XOTP.TICKET_EXPIRE_SECONDS);
        return convert(plain_in + "|" + xotp.getCode(), true);
    }

    @Override
    public String Decrypt(String cipher_in) throws RuntimeException {
        return convert(cipher_in, false);
    }

    /**
     *
     * @param cnv_input : 제한된 범위('0'에서 '9'사이의 문자, 특수문자 '|')의 문자들로 구성된 문자열
     * @param encrypt : 암호화 여부를 결정, (false)이면 복호화
     * @return 성공여부
     */
    private String convert(String cnv_input, boolean encrypt) {
        ArrayList<Character> alphabets = new ArrayList<Character>(Arrays.asList( 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k'));

        int lastIdx = alphabets.size() - 1;

        StringBuilder sb = new StringBuilder();
        if (encrypt) { // 암호화
            byte[] byteArray = cnv_input.getBytes();
            int digit_index = 0;
            for(byte b : byteArray) {
                if ((char)b == '|') {
                    digit_index = lastIdx;
                }
                else {
                    digit_index = b & 0x0f; // 0x30 .. 0x39 사이의 값을 비트조작하여 정수화
                }
            }
            sb.append(alphabets.get(digit_index));
            return internalConvert(sb.toString(), encrypt);
        }
        else { // 복호화
            String cnv_temp = internalConvert(cnv_input, encrypt);
            if (cnv_temp != null) {
                byte[] byteArray = cnv_temp.getBytes();
                for(byte b : byteArray) {
                    int char_index = 0;
                    // 'k'인 경우, '|'로 복원
                    if (alphabets.get(lastIdx) == (char)b) {
                        sb.append("|");
                    }
                    else {
                        char_index = alphabets.indexOf((char)b);
                        assert char_index != -1 && char_index <= 9;
                        sb.append(char_index);
                    }
                } // END of for()
                return sb.toString();
            }
            return null;
        }
    }

    private String internalConvert(String source, boolean encrypt) {
        if (source.length() >= MAX_SOURCE_LENGTH)
            throw new IllegalArgumentException("The length of plaintext for encryption or decryption has been exceeded.");

        char[] srcChars = new char[source.length()];
        char[] keyChars = new char[XOTP.HMAC_KEY.length()];
        int notAlphabets = 0;
        int keySize = keyChars.length;

        source.getChars(0, srcChars.length, srcChars, 0 );
        XOTP.HMAC_KEY.getChars(0, keyChars.length, keyChars, 0);

        StringBuilder sb = new StringBuilder();
        for(int i=0; i < srcChars.length; i++) {
            char c = srcChars[i];
            if (Character.isAlphabetic(c)) {
                boolean isUpper = Character.isUpperCase(c);
                int offset = isUpper ? (int)'A' : (int)'a';
                int key_index = (i - notAlphabets) % keySize;
                int k = (isUpper ? Character.toUpperCase(keyChars[key_index]) : Character.toLowerCase(keyChars[key_index])) - offset;
                k = encrypt ? k : -k;
                char ec = (char)(modEx(((srcChars[i] + k) - offset), 26) + offset);
                sb.append(ec);
            }
            else {
                sb.append(c);
                ++notAlphabets;
            }
        } // END of for()
        return sb.toString();
    }

    private static int modEx(int a, int b) {
        return ((a % b) + b) % b;
    }
}
