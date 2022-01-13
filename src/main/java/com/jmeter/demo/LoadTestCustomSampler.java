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
    private Network network = null;
    private static final String INDEX_TAG = "index";
    private static final String PHASE_TAG = "phase";
    private static final String N = "n";
    public LoadTestCustomSampler(){
        if(network == null) {
            network = createConnection("Admin@fi.example.com");
        }
    }
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(INDEX_TAG, "${csv_index}");
        defaultParameters.addArgument(PHASE_TAG, "0");
        defaultParameters.addArgument(N, "${csv_index.max}");
        return defaultParameters;
    }
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        SampleResult sampleResult = new SampleResult();
        try {
            int csvIndex = Integer.parseInt(javaSamplerContext.getParameter(INDEX_TAG));
            //STATUS: 0-creating addresses, 1-minting allowances, 2-transferring
            int status = Integer.parseInt(javaSamplerContext.getParameter(PHASE_TAG));
            int n = Integer.parseInt(javaSamplerContext.getParameter(N));
            String result = null;
            KeyPair keyPair;
            Signature signature;
            switch (status) {
                case 0:
                    keyPair = getXthKeyOfCSV(csvIndex);
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, keyPair.addressString, "0");
                    sampleResult.sampleStart();
                    result = createTransaction("Admin@fi.example.com", signature, "cbdc", "createAddress", keyPair.addressString, "0");
                    sampleResult.sampleEnd();
                    break;
                case 1:
                    keyPair = getXthKeyOfCSV(csvIndex);
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, keyPair.addressString, "1000", "1");
                    sampleResult.sampleStart();
                    result = createTransaction("Admin@fi.example.com", signature, "cbdc", "mintUnits", keyPair.addressString, "1000", "1");
                    sampleResult.sampleEnd();
                    break;
                case 2:
                    int[] indexes = ThreadLocalRandom.current().ints(0, n).distinct().limit(2).toArray();
                    KeyPair keyPair1 = getXthKeyOfCSV(indexes[0]);
                    KeyPair keyPair2 = getXthKeyOfCSV(indexes[1]);
                    int nonce = Integer.parseInt(createTransaction("Admin@fi.example.com", null, "cbdc", "getNonce", keyPair1.addressString));
                    signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair1, keyPair1.addressString, keyPair2.addressString, "1", String.valueOf(nonce+1));
                    sampleResult.sampleStart();
                    result = createTransaction("Admin@fi.example.com", signature, "cbdc", "transfer", keyPair1.addressString, keyPair2.addressString, "1", String.valueOf(nonce+1));
                    sampleResult.sampleEnd();
                    break;
                default:

                    break;
            }
            sampleResult.setSuccessful(Boolean.TRUE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(result);
            return sampleResult;
        }
        catch(Exception e){
            e.printStackTrace();
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(Boolean.FALSE);
            sampleResult.setResponseMessage(e.getMessage());
            return sampleResult;
        }
    }
    public KeyPair getXthKeyOfCSV(int X){
        try (Stream<String> lines = Files.lines(Paths.get("resources/keys.csv"))) {
            String lineX = lines.skip(X).findFirst().get();
            String[] parts = lineX.split(",");
            String privateKey = parts[0];
            String publicKey = parts[1];
            String address = parts[2];
            return new KeyPair(String.valueOf(X), privateKey, publicKey, address);
        }
        catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }
    public Network createConnection(String identity){
        try {
            String pathRoot = "";
            String walletName = "wallet";
            Path walletDirectory = Paths.get(pathRoot + walletName);
            Path networkConfigFile = Paths.get(pathRoot + "notls/connection.json");
            Wallet wallet = Wallet.createFileSystemWallet(walletDirectory);
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(wallet, identity)
                    .networkConfig(networkConfigFile);

            // Create a gateway connection
            return builder.connect().getNetwork("epengo-channel");
        }
        catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public String createTransaction(String identity, Signature signature, String chaincode, String method, String... args) throws Exception{
        ArrayList<String> argParts = new ArrayList(Arrays.asList(args));
        Contract contract = network.getContract(chaincode);
        byte[] result = null;
        if(signature!=null){
            argParts.add(String.valueOf(signature.v));
            argParts.add(signature.r);
            argParts.add(signature.s);
        }
        //System.out.println("CALL: " + method + " " + Arrays.toString(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class)));
        result = contract.createTransaction(method).submit(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class));
        //System.out.println("RESULT: " + new String(result, StandardCharsets.UTF_8) + "\n");
        return new String(result, StandardCharsets.UTF_8);
    }
}
