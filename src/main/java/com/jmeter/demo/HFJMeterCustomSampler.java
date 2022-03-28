package com.jmeter.demo;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class HFJMeterCustomSampler extends AbstractJavaSamplerClient {
    private int retried = 0;
    private Network network = null;
    private static final String CONNECTION_PATH = "connection.json relative path";
    private static final String WALLET_PATH = "wallet directory relative path";
    private static final String IDENTITY = "identity";
    private static final String CHAINCODE = "chaincode name";
    private static final String METHOD = "method name";
    private static final String CHANNEL = "channel name";
    private static final String ARGS = "args (separated by spaces)";
    private static final String PRIVATE_KEY_STRING = "signing address private key (null if sign not needed)";
    private static final String PUBLIC_KEY_STRING = "signing address public key (null if sign not needed)";
    private static final String ADDRESS_STRING = "signing address (null if sign not needed)";

    private String connectionPath;
    private String walletPath;
    private String identity;
    private String chaincode;
    private String method;
    private String channel;
    private ArrayList<String> arguments;
    private String privateKeyString;
    private String publicKeyString;
    private String addressString;

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(CONNECTION_PATH, "/resources/connection.json");
        defaultParameters.addArgument(WALLET_PATH, "/resources/wallet");
        defaultParameters.addArgument(IDENTITY, "Admin@cb");
        defaultParameters.addArgument(CHAINCODE, "cbdc");
        defaultParameters.addArgument(CHANNEL, "epengo-channel");
        defaultParameters.addArgument(METHOD, "setMintingAllowance");
        defaultParameters.addArgument(ARGS, "FIOrgMSP 50000000");
        defaultParameters.addArgument(PRIVATE_KEY_STRING, "null");
        defaultParameters.addArgument(PUBLIC_KEY_STRING, "null");
        defaultParameters.addArgument(ADDRESS_STRING, "null");
        return defaultParameters;
    }
    public void initializeSampler(JavaSamplerContext javaSamplerContext){
        connectionPath = javaSamplerContext.getParameter(CONNECTION_PATH);
        walletPath = javaSamplerContext.getParameter(WALLET_PATH);
        identity = javaSamplerContext.getParameter(IDENTITY);
        chaincode = javaSamplerContext.getParameter(CHAINCODE);
        method = javaSamplerContext.getParameter(METHOD);
        channel = javaSamplerContext.getParameter(CHANNEL);
        network = createConnection(identity, walletPath, connectionPath, channel);
    }
    @Override
    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        SampleResult sampleResult = new SampleResult();
        Signature signature = null;
        KeyPair keyPair = null;
        try {
            //Create network at first call
            if (network == null) {
                initializeSampler(javaSamplerContext);
            }
            //Get varying params
            privateKeyString = javaSamplerContext.getParameter(PRIVATE_KEY_STRING);
            publicKeyString = javaSamplerContext.getParameter(PUBLIC_KEY_STRING);
            addressString = javaSamplerContext.getParameter(ADDRESS_STRING);
            arguments = new ArrayList(Arrays.asList(javaSamplerContext.getParameter(ARGS).split(" ")));
            //Create signature if needed
            if(!privateKeyString.equals("null")){
                keyPair = new KeyPair(privateKeyString, publicKeyString, addressString);
                signature = SignHomeNativeMessage.createSignatureFromKeyPair(keyPair, arguments.toArray(new String[arguments.size()]));
            }
            //Transaction
            sampleResult.sampleStart();
            String result = createTransaction(network, signature, chaincode, method, arguments.toArray(new String[arguments.size()]));
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(Boolean.TRUE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(result);
            retried = 0;
            return sampleResult;
        }
        catch (Exception e){
            if (retried <= 1) {
                retried++;
                //Sleep
                try {Thread.sleep(10);} catch (InterruptedException ex) {ex.printStackTrace();}
                return runTest(javaSamplerContext);
            }
            e.printStackTrace();
            sampleResult.setSuccessful(Boolean.FALSE);
            sampleResult.setResponseMessage(e.getMessage());
            return sampleResult;
        }
    }
    public Network createConnection(String identity, String walletPath, String connectionPath, String channel){
        try {
            Path walletDirectory = Paths.get(walletPath);
            Path networkConfigFile = Paths.get(connectionPath);
            Wallet wallet = Wallet.createFileSystemWallet(walletDirectory);
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
    public String createTransaction(Network network, Signature signature, String chaincode, String method, String... args) throws Exception{
        ArrayList<String> argParts = new ArrayList(Arrays.asList(args));
        Contract contract = network.getContract(chaincode);
        byte[] result = null;
        if(signature!=null){
            argParts.add(String.valueOf(signature.v));
            argParts.add(signature.r);
            argParts.add(signature.s);
        }
        result = contract.createTransaction(method).submit(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class));
        return new String(result, StandardCharsets.UTF_8);
    }
}