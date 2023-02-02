package ciel.android.libs;

public class TextUtils {
    public static byte[] getBytesFromHexString(String hexString) throws RuntimeException {
        if (hexString.length() % 2 != 0)
            throw new RuntimeException("The length of the String must be Even number.");

        byte[] originBytes = hexString.getBytes();
        byte[] destBytes  = new byte[originBytes.length/2];
        int j = 0;

        for(int i = 0; i <= originBytes.length-1; i +=2) {
            destBytes[j] = getByte(new char[]{(char)originBytes[i], (char)originBytes[i+1]});
            j++;
        }
        return destBytes;
    }

    public static byte getByte(char[] hexChars) throws RuntimeException {
        if (hexChars.length != 2)
            throw new RuntimeException("The length of the byte array must be 2 byte long.");
        byte msb = charToHex(hexChars[0]);
        byte lsb = charToHex(hexChars[1]);
        return (byte) ((msb << 4) | (lsb & 0xf));
    }

    private static byte charToHex(char c)
    {
        byte b = -1;
        if (c >= '0' && c <= '9')
            b = (byte) (c - '0');
        else if (c >= 'A' && c <= 'F')
            b = (byte) (c - 'A' + 10);
        else if (c >= 'a' && c <= 'f')
            b = (byte) (c - 'a' + 10);
        return b;
    }
}
