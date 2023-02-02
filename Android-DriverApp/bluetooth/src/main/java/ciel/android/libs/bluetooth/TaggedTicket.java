package ciel.android.libs.bluetooth;

public class TaggedTicket {

    private CertifiedResult mCertified = CertifiedResult.Success;
    private String mPhoneNum;
    private String mTaggedTime;

    // require Constructor
    public TaggedTicket(String phone, String taggedTime) {
        this(phone, taggedTime, CertifiedResult.Success);
    }

    public TaggedTicket(String phone, String taggedTime, CertifiedResult result) {
        mPhoneNum = phone;
        mTaggedTime = taggedTime;
        mCertified = result;
    }

    public void setCertified(CertifiedResult value) {
        mCertified = value;
    }
    public void setPhoneNum(String value) {
        mPhoneNum = value;
    }
    public void setTaggedTime(String value) {
        mTaggedTime = value;
    }

    public CertifiedResult getResult() {
        return mCertified;
    }
    public String getPhoneNum() {
        return mPhoneNum;
    }
    public String getTaggedTime() {
        return mTaggedTime;
    }

    public enum CertifiedResult {
        Success(1), DataSourceError(2), FormatError(3), FailedOTP(4), NotMatchedDeviceCTN(5), AnyError(0);

        CertifiedResult(int i) {
        }

        // private int resultInt;
        public static CertifiedResult ToCertifiedResult(String strEnumName) {
            try {
                return valueOf(strEnumName);
            }
            catch (IllegalArgumentException ex) {
                ex.printStackTrace();
                return AnyError;
            }
        }
    }
}
