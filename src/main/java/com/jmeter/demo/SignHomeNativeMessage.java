package com.jmeter.demo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;

public class SignHomeNativeMessage {
    private static final Type KEYPAIR_TYPE = new TypeToken<KeyPairs>(){}.getType();
    public static Signature createSignature(int signingKeyIndex, String messageParts) {
        Gson gson = new Gson();
        try {
            //Reading keys
            JsonReader reader = new JsonReader(new FileReader("resources/keys.json"));
            KeyPairs keyPairs = gson.fromJson(reader, KEYPAIR_TYPE);

            //Handling arguments
            if ((signingKeyIndex < 0) || (signingKeyIndex >= keyPairs.keypair.size())) {
                System.err.println("ERROR: Key index (" + signingKeyIndex + ") out of range [0," + (keyPairs.keypair.size() - 1) + "]");
                System.exit(1);
            }
            //Create message
            byte[] message = String.join(" ", messageParts).getBytes();
            //KeyPair
            ECKeyPair ecKeyPair = new ECKeyPair(Numeric.toBigInt(keyPairs.keypair.get(signingKeyIndex).privateKeyString), Numeric.toBigInt(keyPairs.keypair.get(signingKeyIndex).publicKeyString));
            //Signature
            Sign.SignatureData signData = Sign.signPrefixedMessage(message, ecKeyPair);
            return new Signature(signData);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
