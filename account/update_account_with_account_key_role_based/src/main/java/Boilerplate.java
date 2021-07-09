import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.klaytn.caver.Caver;
import com.klaytn.caver.account.Account;
import com.klaytn.caver.account.WeightedMultiSigOptions;
import com.klaytn.caver.methods.response.AccountKey;
import com.klaytn.caver.methods.response.Bytes32;
import com.klaytn.caver.methods.response.TransactionReceipt;
import com.klaytn.caver.transaction.TxPropertyBuilder;
import com.klaytn.caver.transaction.response.PollingTransactionReceiptProcessor;
import com.klaytn.caver.transaction.response.TransactionReceiptProcessor;
import com.klaytn.caver.transaction.type.AccountUpdate;
import com.klaytn.caver.transaction.type.ValueTransfer;
import com.klaytn.caver.wallet.keyring.RoleBasedKeyring;
import com.klaytn.caver.wallet.keyring.SingleKeyring;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.Credentials;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Boilerplate code about "How to Update Klaytn Account Keys with Caver #3 — AccountKeyRoleBased"
 * Related article - Korean: https://medium.com/klaytn/klaytn-%EC%82%AC%EC%9A%A9%EC%84%B1-%EA%B0%9C%EC%84%A0-series-4-%ED%94%8C%EB%9E%AB%ED%8F%BC%EC%97%90%EC%84%9C%EC%9D%98-role-based-key-%EC%A7%80%EC%9B%90-216a34b959c3
 * Related article - English: https://medium.com/klaytn/klaytn-usability-improvement-series-4-supporting-role-based-keys-on-the-platform-level-e2c912672b7b
 */
public class Boilerplate {
    // You can directly input values for the variables below, or you can enter values in the caver-java-examples/.env file.
    private static String nodeApiUrl = ""; // e.g. "https://node-api.klaytnapi.com/v1/klaytn";
    private static String accessKeyId = ""; // e.g. "KASK1LVNO498YT6KJQFUPY8S";
    private static String secretAccessKey = ""; // e.g. "aP/reVYHXqjw3EtQrMuJP4A3/hOb69TjnBT3ePKG";
    private static String chainId = ""; // e.g. "1001" or "8217";
    private static String senderAddress = ""; // e.g. "0xeb709d59954f4cdc6b6f3bfcd8d531887b7bd199"
    private static String senderPrivateKey = ""; // e.g. "0x42f6375b608c2572fadb2ed9fd78c5c456ca3aa860c43192ad910c3269727fc7"
    private static String recipientAddress = ""; // e.g. "0xeb709d59954f4cdc6b6f3bfcd8d531887b7bd199"


    public static void main(String[] args) {
        try {
            loadEnv();
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String objectToString(Object value) throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(value);
    }

    public static void loadEnv() {
        Dotenv env;
        String workingDirectory = System.getProperty("user.dir");
        Path workingDirectoryPath = Paths.get(workingDirectory);
        String projectRootDirectory = "caver-java-examples";
        String currentDirectoryName = workingDirectoryPath.getName(workingDirectoryPath.getNameCount() - 1).toString();
        String envDirectory = currentDirectoryName.equals(projectRootDirectory) ?
                workingDirectoryPath.toString() :
                workingDirectoryPath.getParent().getParent().toString();

        // Read `/path/to/caver-java-examples/.env` file.
        env = Dotenv.configure().directory(envDirectory).load();

        nodeApiUrl = nodeApiUrl.equals("") ? env.get("NODE_API_URL") : nodeApiUrl;
        accessKeyId = accessKeyId.equals("") ? env.get("ACCESS_KEY_ID") : accessKeyId;
        secretAccessKey = secretAccessKey.equals("") ? env.get("SECRET_ACCESS_KEY") : secretAccessKey;
        chainId = chainId.equals("") ? env.get("CHAIN_ID") : chainId;
        senderAddress = senderAddress.equals("") ? env.get("SENDER_ADDRESS") : senderAddress;
        senderPrivateKey = senderPrivateKey.equals("") ? env.get("SENDER_PRIVATE_KEY") : senderPrivateKey;
        recipientAddress = recipientAddress.equals("") ? env.get("RECIPIENT_ADDRESS") : recipientAddress;
    }

    public static void run() throws Exception {
        System.out.println("=====> Update AccountKey to AccountKeyRoleBased");

        HttpService httpService = new HttpService(nodeApiUrl);
        httpService.addHeader("Authorization", Credentials.basic(accessKeyId, secretAccessKey));
        httpService.addHeader("x-chain-id", chainId);
        Caver caver = new Caver(httpService);

        // Add keyring to in-memory wallet
        SingleKeyring senderKeyring = caver.wallet.keyring.create(senderAddress, senderPrivateKey);
        caver.wallet.add(senderKeyring);

        List<String[]> newRoleBasedKeys = caver.wallet.keyring.generateRolBasedKeys(new int[]{2, 1, 3});
        System.out.println("new private keys by role: " + objectToString(newRoleBasedKeys));

        // Create new Keyring instance as RoleBasedKeyring with new private keys by role
        RoleBasedKeyring newKeyring = caver.wallet.keyring.create(senderKeyring.getAddress(), newRoleBasedKeys);
        BigInteger[][] optionWeight = {
                {BigInteger.ONE, BigInteger.ONE},
                {},
                {BigInteger.valueOf(2), BigInteger.ONE, BigInteger.ONE}
        };
        WeightedMultiSigOptions[] options = {
                new WeightedMultiSigOptions(BigInteger.valueOf(2), Arrays.asList(optionWeight[0])),
                new WeightedMultiSigOptions(),
                new WeightedMultiSigOptions(BigInteger.valueOf(3), Arrays.asList(optionWeight[2]))
        };
        // Create an Account instance that includes the address and the role based key
        Account account = newKeyring.toAccount(Arrays.asList(options));

        // Create account update transaction instance
        AccountUpdate accountUpdate = caver.transaction.accountUpdate.create(
                TxPropertyBuilder.accountUpdate()
                        .setFrom(senderKeyring.getAddress())
                        .setAccount(account)
                        .setGas(BigInteger.valueOf(150000))
        );

        // Sign the transaction
        caver.wallet.sign(senderKeyring.getAddress(), accountUpdate);
        // Send transaction
        Bytes32 sendResult = caver.rpc.klay.sendRawTransaction(accountUpdate).send();
        if(sendResult.hasError()) {
            throw new TransactionException(sendResult.getError().getMessage());
        }
        String txHash = sendResult.getResult();
        TransactionReceiptProcessor receiptProcessor = new PollingTransactionReceiptProcessor(caver, 1000, 15);
        TransactionReceipt.TransactionReceiptData receiptData = receiptProcessor.waitForTransactionReceipt(txHash);
        System.out.println("Account Update Transaction receipt => ");
        System.out.println(objectToString(receiptData));

        // Get accountKey from network
        AccountKey accountKey = caver.rpc.klay.getAccountKey(senderKeyring.getAddress()).send();
        System.out.println("Result of account key update to AccountKeyRoleBased");
        System.out.println("Account address: " + senderKeyring.getAddress());
        System.out.println("accountKey => ");
        System.out.println(objectToString(accountKey));

        // Update keyring with new private key in in-memory wallet
        caver.wallet.updateKeyring(newKeyring);
        // Send 1 Peb to recipient to test whether updated accountKey is well-working or not.
        ValueTransfer vt = caver.transaction.valueTransfer.create(
                TxPropertyBuilder.valueTransfer()
                        .setFrom(senderKeyring.getAddress())
                        .setTo(recipientAddress)
                        .setValue(BigInteger.valueOf(1))
                        .setGas(BigInteger.valueOf(150000))
        );

        // Sign the transaction with updated keyring
        // This sign function will sign the transaction with all private keys in RoleTrasnsactionKey in the keyring
        caver.wallet.sign(senderKeyring.getAddress(), vt);
        // Send transaction
        Bytes32 vtResult = caver.rpc.klay.sendRawTransaction(vt).send();
        TransactionReceipt.TransactionReceiptData vtReceiptData = receiptProcessor.waitForTransactionReceipt(vtResult.getResult());
        System.out.println("After account update value transfer transaction receipt => ");
        System.out.println(objectToString(vtReceiptData));
    }
}
