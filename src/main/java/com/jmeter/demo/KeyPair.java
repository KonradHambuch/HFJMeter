package com.jmeter.demo;

class KeyPair{
    public String id;
    public String privateKeyString;
    public String publicKeyString;
    public String addressString;

    public KeyPair(String id, String privateKeyString, String publicKeyString, String addressString) {
        this.id = id;
        this.privateKeyString = privateKeyString;
        this.publicKeyString = publicKeyString;
        this.addressString = addressString;
    }
}