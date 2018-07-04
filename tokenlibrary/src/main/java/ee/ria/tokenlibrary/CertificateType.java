package ee.ria.tokenlibrary;

public enum CertificateType {

    AUTHENTICATION((byte) 0xAA),
    SIGNING((byte) 0xDD);

    public byte value;

    CertificateType(byte value) {
        this.value = value;
    }
}
