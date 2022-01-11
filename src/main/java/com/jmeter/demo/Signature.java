package com.jmeter.demo;

import org.apache.commons.codec.binary.Base64;
import org.web3j.crypto.Sign;

public class Signature {
    public int v;
    public String r;
    public String s;
    Signature(Sign.SignatureData signatureData){
        this.v = signatureData.getV()[0] & 0xFF;
        this.r = Base64.encodeBase64String(signatureData.getR());
        this.s = Base64.encodeBase64String(signatureData.getS());
    }
}
