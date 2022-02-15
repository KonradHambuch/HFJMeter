package com.jmeter.demo;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class LoadTestCustomSampler extends AbstractJavaSamplerClient {
    private int retried = 0;
    private Network network = null;
    private static final String INDEX_TAG = "index";
    private static final String PHASE_TAG = "phase";
    private static final String N = "n";
    private static final String CONNECTION_PATH = "connection.json relative path";
    private static final String WALLET_PATH = "wallet directory relative path";
    private static final String IDENTITY = "identity";
    private static final String KEYS_PATH = "keys.csv relative path";
    private String index;
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(INDEX_TAG, "${csv_index}");
        defaultParameters.addArgument(PHASE_TAG, "0");
        defaultParameters.addArgument(N, "${csv_index.max}");
        defaultParameters.addArgument(CONNECTION_PATH, "resources/notls/connection.json");
        defaultParameters.addArgument(WALLET_PATH, "resources/wallet");
        defaultParameters.addArgument(IDENTITY, "Admin@fi.example.com");
        defaultParameters.addArgument(KEYS_PATH, "resources/keys.csv");
        return defaultParameters;
    }
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        int csvIndex = Integer.parseInt(javaSamplerContext.getParameter(INDEX_TAG));
        //STATUS: 0-set minting allowance, 1-creating addresses, 2-minting allowances, 3-transferring
        int status = Integer.parseInt(javaSamplerContext.getParameter(PHASE_TAG));
        int n = Integer.parseInt(javaSamplerContext.getParameter(N));
        String connectionPath = javaSamplerContext.getParameter(CONNECTION_PATH);
        String walletPath = javaSamplerContext.getParameter(WALLET_PATH);
        String keysPath = javaSamplerContext.getParameter(KEYS_PATH);
        String identity = javaSamplerContext.getParameter(IDENTITY);
        if (network == null) {
            network = Utils.createConnection(identity, walletPath, connectionPath, "epengo-channel");
        }
        SampleResult sampleResult = new SampleResult();
        try {
            String result = null;
            KeyPair keyPair;
            Signature signature;
            switch (status) {
                case 0:
                    sampleResult.sampleStart();
                    result = Utils.createTransaction(network, null, "cbdc", "setMintingAllowance", "FIOrgMSP", "500000000");
                    sampleResult.sampleEnd();
                    break;
                case 1:
                    keyPair = Utils.getXthKeyOfCSV(csvIndex, keysPath);
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, keyPair.addressString, "0");
                    sampleResult.sampleStart();
                    result = Utils.createTransaction(network, signature, "cbdc", "createAddress", keyPair.addressString, "0");
                    sampleResult.sampleEnd();
                    break;
                case 2:
                    keyPair = Utils.getXthKeyOfCSV(csvIndex, keysPath);
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, keyPair.addressString, "1000", "1");
                    sampleResult.sampleStart();
                    result = Utils.createTransaction(network, signature, "cbdc", "mintUnits", keyPair.addressString, "1000", "1");
                    sampleResult.sampleEnd();
                    break;
                case 3:
                    int[] indexes = ThreadLocalRandom.current().ints(0, n).distinct().limit(2).toArray();
                    KeyPair keyPair1 = Utils.getXthKeyOfCSV(indexes[0], keysPath);
                    KeyPair keyPair2 = Utils.getXthKeyOfCSV(indexes[1], keysPath);
                    int nonce = Integer.parseInt(Utils.createTransaction(network, null, "cbdc", "getNonce", keyPair1.addressString));
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair1, keyPair1.addressString, keyPair2.addressString, "1", String.valueOf(nonce + 1));
                    sampleResult.sampleStart();
                    result = Utils.createTransaction(network, signature, "cbdc", "transfer", keyPair1.addressString, keyPair2.addressString, "1", String.valueOf(nonce + 1));
                    sampleResult.sampleEnd();
                    break;
                default:

                    break;
            }
            sampleResult.setSuccessful(Boolean.TRUE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(result);
            retried = 0;
            return sampleResult;
        } catch (Exception e) {
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
