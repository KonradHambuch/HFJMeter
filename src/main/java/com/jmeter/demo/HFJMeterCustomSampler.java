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
    private static final String CHAINCODE_TAG = "chaincode";
    private static final String METHOD_TAG = "method";
    private static final String ARGS_TAG = "args (space between)";
    private static final String SIGN_INDEX = "Index from keys.json (negative if no signature needed)";

    @Override
    public Arguments getDefaultParameters() {
        Arguments defaultParameters = new Arguments();
        defaultParameters.addArgument(CHAINCODE_TAG, "cbdc");
        defaultParameters.addArgument(METHOD_TAG, "addressExists");
        defaultParameters.addArgument(SIGN_INDEX, "-1");
        defaultParameters.addArgument(ARGS_TAG, "0x1b18cf7b9bd974a9b2f2c4d5c0b1c2f69022dc2b");
        return defaultParameters;
    }

    public SampleResult runTest(JavaSamplerContext javaSamplerContext) {
        String chaincode = javaSamplerContext.getParameter(CHAINCODE_TAG);
        String method = javaSamplerContext.getParameter(METHOD_TAG);
        String args1 = javaSamplerContext.getParameter(ARGS_TAG);
        String signIndex = javaSamplerContext.getParameter(SIGN_INDEX);
        int index;
        Signature signature;
        //Validate and Parse JMeter Parameters
        ArrayList<String> argParts = new ArrayList<String>(Arrays.asList(args1.split(" ")));
        SampleResult sampleResult = new SampleResult();
        sampleResult.sampleStart();
        try {
            System.out.println(System.getProperty("user.dir"));
            String pathRoot = "";
            String walletName = "wallet";
            Path walletDirectory = Paths.get(pathRoot + walletName);
            System.out.println(walletDirectory.toAbsolutePath().toString());
            Path networkConfigFile = Paths.get(pathRoot + "notls/connection.json");
            Wallet wallet = Wallet.createFileSystemWallet(walletDirectory);
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(wallet, "Admin@fi.example.com")
                    .networkConfig(networkConfigFile);

            // Create a gateway connection
            try (Gateway gateway = builder.connect()) {
                // Obtain a smart contract deployed on the network.
                Network network = gateway.getNetwork("epengo-channel");
                Contract contract = network.getContract(chaincode);
                byte[] result = null;
                if((index = Integer.parseInt(signIndex)) > -1){
                    signature = SignHomeNativeMessage.createSignature(index, args1);
                    argParts.add(String.valueOf(signature.v));
                    argParts.add(signature.r);
                    argParts.add(signature.s);
                    System.out.println(signature.v + " " + signature.s + " " + signature.r);
                }
                System.out.println(method+ " " );
                for(String a: Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class)){
                    System.out.println(a);
                }
                result = contract.createTransaction(method).submit(Arrays.copyOf(argParts.toArray(), argParts.size(), String[].class));
                System.out.println(new String(result, StandardCharsets.UTF_8));
                sampleResult.sampleEnd();
                sampleResult.setSuccessful(Boolean.TRUE);
                sampleResult.setResponseCodeOK();
                sampleResult.setResponseMessage(new String(result, StandardCharsets.UTF_8));
                return sampleResult;
            } catch (Exception e) {
                e.printStackTrace();
                sampleResult.sampleEnd();
                sampleResult.setSuccessful(Boolean.FALSE);
                sampleResult.setResponseCodeOK();
                sampleResult.setResponseMessage(e.getMessage());
                return sampleResult;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sampleResult.sampleEnd();
            sampleResult.setSuccessful(Boolean.FALSE);
            sampleResult.setResponseCodeOK();
            sampleResult.setResponseMessage(e.getMessage());
            return sampleResult;
        }
    }
}
