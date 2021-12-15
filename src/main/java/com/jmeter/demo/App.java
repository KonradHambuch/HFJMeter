package com.jmeter.demo;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.gateway.spi.WalletStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

public class App
{
    public static void main( String[] args )
    {
        try{
            String pathRoot = "";
            String walletName = "wallet\\Admin@fi.example.com";
            Path walletDirectory = Paths.get(pathRoot + walletName);
            System.out.println(walletDirectory.toAbsolutePath().toString());
            //Path walletPath_2 = Paths.get(pathRoot + "org-2-wallet");
            Path networkConfigFile = Paths.get(pathRoot + "notls\\connection.json");
            //Path networkConfigPath_2 = Paths.get(pathRoot + "2-Org-Local-Fabric-Org2_connection.json");
            // Load an existing wallet holding identities used to access the network.
            //Path walletDirectory = Paths.get("resources/connection/wallet");
            Wallet wallet = Wallets.newFileSystemWallet(walletDirectory);

            // Path to a common connection profile describing the network.
            //Path networkConfigFile = Paths.get("resources/connection/connection.json");

            // Configure the gateway connection used to access the network.
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(wallet, "Admin")
                    .networkConfig(networkConfigFile);

            // Create a gateway connection
            try (Gateway gateway = builder.connect()) {

                // Obtain a smart contract deployed on the network.
                Network network = gateway.getNetwork("e-pengo-channel");
                Contract contract = network.getContract("cbdc");

                // Submit transactions that store state to the ledger.
                byte[] result = contract.createTransaction("getAllClients")
                        .submit();
                System.out.println(new String(result, StandardCharsets.UTF_8));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
}
