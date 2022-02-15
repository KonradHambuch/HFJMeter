package com.jmeter.demo;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.hyperledger.fabric.gateway.Network;

import java.util.ArrayList;
import java.util.Arrays;

public class HFJMeterCustomSampler extends AbstractJavaSamplerClient {
    private int retried = 0;
    private Network network = null;
    private static final String CONNECTION_PATH = "connection.json relative path";
    private static final String WALLET_PATH = "wallet directory relative path";
    private static final String IDENTITY = "identity";
    private static final String KEYS_PATH = "keys.csv relative path";
    private static final String CHAINCODE = "chaincode name";
    private static final String METHOD = "method name";
    private static final String CHANNEL = "channel name";
    private static final String ARGS = "args (separated by spaces)";
    private static final String PRIVATE_KEY_STRING = "signing address private key (null if sign not needed)";
    private static final String PUBLIC_KEY_STRING = "signing address public key (null if sign not needed)";
    private static final String ADDRESS_STRING = "signing address (null if sign not needed)";
    private static final String NONCE_NEEDED = "0/1 whether nonce tx is needed before tx";

    private String connectionPath;
    private String walletPath;
    private String keysPath;
    private String identity;
    private String chaincode;
    private String method;
    private String channel;
    private ArrayList<String> arguments;
    private String privateKeyString;
    private String publicKeyString;
    private String addressString;
    private int nonceNeeded;

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(CONNECTION_PATH, "resources/notls/connection.json");
        defaultParameters.addArgument(WALLET_PATH, "resources/wallet");
        defaultParameters.addArgument(KEYS_PATH, "resources/keys.csv");
        defaultParameters.addArgument(IDENTITY, "Admin@fi.example.com");
        defaultParameters.addArgument(CHAINCODE, "cbdc");
        defaultParameters.addArgument(CHANNEL, "epengo-channel");
        defaultParameters.addArgument(METHOD, "setMintingAllowance");
        defaultParameters.addArgument(ARGS, "FIOrgMSP 50000000");
        defaultParameters.addArgument(PRIVATE_KEY_STRING, "null");
        defaultParameters.addArgument(PUBLIC_KEY_STRING, "null");
        defaultParameters.addArgument(ADDRESS_STRING, "null");
        defaultParameters.addArgument(NONCE_NEEDED, "0");
        return defaultParameters;
    }
    public void initializeSampler(JavaSamplerContext javaSamplerContext){
        connectionPath = javaSamplerContext.getParameter(CONNECTION_PATH);
        walletPath = javaSamplerContext.getParameter(WALLET_PATH);
        keysPath = javaSamplerContext.getParameter(KEYS_PATH);
        identity = javaSamplerContext.getParameter(IDENTITY);
        chaincode = javaSamplerContext.getParameter(CHAINCODE);
        method = javaSamplerContext.getParameter(METHOD);
        channel = javaSamplerContext.getParameter(CHANNEL);
        privateKeyString = javaSamplerContext.getParameter(PRIVATE_KEY_STRING);
        publicKeyString = javaSamplerContext.getParameter(PUBLIC_KEY_STRING);
        addressString = javaSamplerContext.getParameter(ADDRESS_STRING);
        nonceNeeded = javaSamplerContext.getIntParameter(NONCE_NEEDED);
        arguments = new ArrayList(Arrays.asList(javaSamplerContext.getParameter(ARGS).split(" ")));

        network = Utils.createConnection(identity, walletPath, connectionPath, channel);
    }
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        SampleResult sampleResult = new SampleResult();
        Signature signature = null;
        int nonce = 0;
        try {
            //Get params at first call
            if (network == null) {
                initializeSampler(javaSamplerContext);
            }
            //Signature
            if(privateKeyString != "null"){
                KeyPair keyPair = new KeyPair(privateKeyString, publicKeyString, addressString);
                signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, (String[]) arguments.toArray());
                if(nonceNeeded != 0){
                    nonce = Integer.parseInt(Utils.createTransaction(network, null, chaincode, "getNonce", keyPair.addressString));
                    arguments.add(String.valueOf(nonce));
                }
            }

            //Get nonce
            //Transaction
            sampleResult.sampleStart();
            String result = Utils.createTransaction(network, signature, chaincode, method, (String[]) arguments.toArray());
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(Boolean.TRUE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(result);
            retried = 0;
            return sampleResult;
        }
        catch (Exception e){
            if (retried <= 3) {
                retried++;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                return runTest(javaSamplerContext);
            }
            e.printStackTrace();
            sampleResult.setSuccessful(Boolean.FALSE);
            sampleResult.setResponseMessage(e.getMessage());
            return sampleResult;
        }
    }
}
