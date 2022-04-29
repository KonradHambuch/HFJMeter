package com.jmeter.demo;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.hyperledger.fabric.gateway.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HFJMeterCustomSamplerWithRandomTransfer extends AbstractJavaSamplerClient {
    private static volatile Map<String, Integer> nonces = new HashMap<String, Integer>();
    private int retried = 0;
    private Network network = null;
    private static final String CONNECTION_PATH = "connection.json relative path";
    private static final String WALLET_PATH = "wallet directory relative path";
    private static final String IDENTITY = "identity";
    private static final String CHAINCODE = "chaincode name";
    private static final String METHOD = "method name";
    private static final String SUBMIT = "submit/evaluate (submitted txs are recorded on ledger)";
    private static final String CHANNEL = "channel name";
    private static final String NONCES_FILE = "nonces file path";
    private static final String ARGS = "args (separated by spaces)";
    private static final String PRIVATE_KEY_STRING = "signing address private key (null if sign not needed)";
    private static final String PUBLIC_KEY_STRING = "signing address public key (null if sign not needed)";
    private static final String ADDRESS_STRING = "signing address (null if sign not needed)";


    private String connectionPath;
    private String walletPath;
    private String identity;
    private String chaincode;
    private String method;
    private String submit;
    private String channel;
    private ArrayList<String> arguments = new ArrayList<>();
    private String noncesFile;
    private String privateKeyString;
    private String publicKeyString;
    private String addressString;
    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(CONNECTION_PATH, "/resources/connection.json");
        defaultParameters.addArgument(WALLET_PATH, "/resources/wallet");
        defaultParameters.addArgument(IDENTITY, "Admin@fi");
        defaultParameters.addArgument(CHAINCODE, "cbdc");
        defaultParameters.addArgument(CHANNEL, "epengo-channel");
        defaultParameters.addArgument(METHOD, "transfer");
        defaultParameters.addArgument(SUBMIT, "submit");
        defaultParameters.addArgument(ARGS, "");
        defaultParameters.addArgument(NONCES_FILE, "/resources/keys/nonces.txt");
        defaultParameters.addArgument(PRIVATE_KEY_STRING, "null");
        defaultParameters.addArgument(PUBLIC_KEY_STRING, "null");
        defaultParameters.addArgument(ADDRESS_STRING, "null");
        return defaultParameters;
    }
    public void initializeSampler(JavaSamplerContext javaSamplerContext){
        try {
            noncesFile = javaSamplerContext.getParameter(NONCES_FILE);
            File nonceFile = new File(noncesFile);
            if(!nonceFile.exists()){
                nonceFile.createNewFile();
            }
            //serializeObject(noncesFile, nonces);
            connectionPath = javaSamplerContext.getParameter(CONNECTION_PATH);
            walletPath = javaSamplerContext.getParameter(WALLET_PATH);
            identity = javaSamplerContext.getParameter(IDENTITY);
            chaincode = javaSamplerContext.getParameter(CHAINCODE);
            method = javaSamplerContext.getParameter(METHOD);
            submit = javaSamplerContext.getParameter(SUBMIT);
            channel = javaSamplerContext.getParameter(CHANNEL);
            network = createConnection(identity, walletPath, connectionPath, channel);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        Signature signature = null;
        KeyPair keyPair = null;

        //Create network at first call
        if (network == null) {
            initializeSampler(javaSamplerContext);
        }
        privateKeyString = javaSamplerContext.getParameter(PRIVATE_KEY_STRING);
        publicKeyString = javaSamplerContext.getParameter(PUBLIC_KEY_STRING);
        addressString = javaSamplerContext.getParameter(ADDRESS_STRING);
        arguments = new ArrayList(Arrays.asList(javaSamplerContext.getParameter(ARGS).split(" ")));
        if(!nonces.containsKey(addressString)){
            nonces.put(addressString,2);
        }
        else{
            nonces.put(addressString, nonces.get(addressString)+1);
        }
        arguments.add(String.valueOf(nonces.get(addressString)));
        if(!privateKeyString.equals("null")){
            keyPair = new KeyPair(privateKeyString, publicKeyString, addressString);
            signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, arguments.toArray(new String[arguments.size()]));
        }
        //Transaction
        return createTransaction(network, signature, chaincode, method, submit, keyPair, arguments.toArray(new String[arguments.size()]));

    }
    public Network createConnection(String identity, String walletPath, String connectionPath, String channel){
        try {
            Path walletDirectory = Paths.get(walletPath);
            Path networkConfigFile = Paths.get(connectionPath);
            Wallet wallet = Wallets.newFileSystemWallet(walletDirectory);
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(wallet, identity)
                    .networkConfig(networkConfigFile);
            return builder.connect().getNetwork(channel);
        }
        catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }
    public SampleResult createTransaction(Network network, Signature signature, String chaincode, String method, String submit, KeyPair signingKey, String... args) {
        SampleResult sampleResult = new SampleResult();
        try {
            ArrayList<String> argParts = new ArrayList(Arrays.asList(args));
            Contract contract = network.getContract(chaincode);
            byte[] result = null;
            if (signature != null) {
                argParts.add(String.valueOf(signature.v));
                argParts.add(signature.r);
                argParts.add(signature.s);
            }
            sampleResult.sampleStart();
            if (submit.equals("evaluate")){
                result = contract.createTransaction(method).evaluate(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class));
            }else{
                result = contract.createTransaction(method).submit(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class));
            }
            //serializeObject(noncesFile, nonces);
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(Boolean.TRUE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(new String(result, StandardCharsets.UTF_8));
            return sampleResult;
        }
        catch(Exception e){
            if(e.getMessage().contains("nonce")){
                e.printStackTrace();
                //Get good nonce
                System.out.println("OLD NONCE of "+ addressString + ": " + args[3]);
                Integer nonce = getNonceOnException(args[0])+1;
                System.out.println("NEW NONCE of "+ addressString + ": " + nonce);
                nonces.put(signingKey.addressString, nonce);
                args[3] = String.valueOf(nonce);
                //Sign tx
                signature = SignHomeNativeMessage.createSignatureFromKeyPair(signingKey, args);
                return createTransaction(network, signature, chaincode, method, submit, signingKey, args);
            }
            if (retried <= 1) {
                retried++;
                //Sleep
                try {Thread.sleep(10);} catch (InterruptedException ex) {ex.printStackTrace();}
                return createTransaction(network, signature, chaincode, method, submit, signingKey, args);
            }
            e.printStackTrace();
            sampleResult.setSuccessful(Boolean.FALSE);
            sampleResult.setResponseMessage(e.getMessage());
            return sampleResult;
        }
    }
    public int getNonceOnException(String address) {
        try{
            return Integer.parseInt(createTransaction(network, null, chaincode, "getNonce","evaluate", null, address).getResponseMessage());
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return -1;
    }
}