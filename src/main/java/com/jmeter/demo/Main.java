package com.jmeter.demo;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String chaincode = "cbdc";
        String method = "addressExists";
        String args1 = "0x1b18cf7b9bd974a9b2f2c4d5c0b1c2f69022dc2b";
        String signIndex = "-1";
        int index;
        Signature signature;
        //Validate and Parse JMeter Parameters
        ArrayList<String> argParts = new ArrayList<String>(Arrays.asList(args1.split(" ")));
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
                if(result!=null) System.out.println(new String(result, StandardCharsets.UTF_8));

            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
