package com.jmeter.demo;

class KeyPair{
    public String privateKeyString;
    public String publicKeyString;
    public String addressString;

    public KeyPair(String privateKeyString, String publicKeyString, String addressString) {
        this.privateKeyString = privateKeyString;
        this.publicKeyString = publicKeyString;
        this.addressString = addressString;
    }
}