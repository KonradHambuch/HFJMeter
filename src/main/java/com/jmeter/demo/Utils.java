package com.jmeter.demo;

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
import java.util.stream.Stream;

public class Utils {
    public static KeyPair getXthKeyOfCSV(int X, String keysPath) throws IOException {
        Stream<String> lines = Files.lines(Paths.get(keysPath));
        String lineX = lines.skip(X).findFirst().get();
        String[] parts = lineX.split(",");
        String privateKey = parts[0];
        String publicKey = parts[1];
        String address = parts[2];
        lines.close();
        return new KeyPair(privateKey, publicKey, address);
    }
    public static Network createConnection(String identity, String walletPath, String connectionPath, String channel){
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
    public static String createTransaction(Network network, Signature signature, String chaincode, String method, String... args) throws Exception{
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
