package org.fermat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.commons.cli.*;
import org.fermatj.core.*;
import org.fermatj.params.MainNetParams;
import org.fermatj.params.RegTestParams;
import org.fermatj.params.TestNet3Params;
import org.fermatj.script.Script;
import org.fermatj.store.BlockStoreException;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.util.encoders.Hex;

public class Main {
    // static variables
    private static NetworkParameters networkParameters; //the network parameters of the network
    private static HelpFormatter formatter;
    private static CommandLine cmd;
    private static Options options;

    private static ECKey privateKey;
    private static Sha256Hash transactionHash;
    private static Sha256Hash blockHash;
    private static Script redeemScript;
    private static RedeemTransaction redeemTransaction;



    //gets the logger
    public static Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    public static NetworkParameters getNetworkParameters(){
        return networkParameters;
    }

    public static void main(String[] args) {
        options = defineOptions();

        CommandLineParser parser = new DefaultParser();
        formatter = new HelpFormatter();

        try {
            cmd = parser.parse(options, args);

            if (!isMandatoryArguments()){
                System.err.println("-p [PrivateKey], -r [RedeemScript], -t [TransactionHash] and -b [BlockHash]  arguments are mandatory.\n");
                formatter.printHelp("FermatPreminingRedeemer", options);
                System.exit(-1);
            }
        } catch (ParseException e) {
            formatter.printHelp("FermatPreminingRedeemer", options);
            System.exit(-1);
        }

        //change the loggin level if passed as a parameter
        if (cmd.hasOption("d"))
            logger.setLevel(Level.DEBUG);
        else
            logger.setLevel(Level.ERROR);

        // if is help show help and exit
        if (cmd.hasOption("h")){
            formatter.printHelp("Help", options);
            System.exit(0);
        }

        //network type
        defineNetwork();

        //gets the specified private key
        String strPrivateKey = cmd.getOptionValue("p");
        if (!isPrivateKeyValid(strPrivateKey)){
            System.err.println("The specified private key " + strPrivateKey + " is not valid on network " + networkParameters.getPaymentProtocolId());
            System.exit(-1);
        }

        // gets the RedeemScript specified.
        String strRedeemScript = cmd.getOptionValue("r");
        try{
            redeemScript = new Script(Hex.decode(strRedeemScript));
        } catch (Exception e){
            System.err.println("The provided Redeem Script (" + strRedeemScript + ") is unparsable.");
            System.exit(-1);
        }


        // gets the Transaction Hash specified.
        String strTxHash = cmd.getOptionValue("t");
        try{
            transactionHash = Sha256Hash.wrap(strTxHash);
        } catch (Exception e){
            System.err.println("The provided transaction Hash (" + strTxHash + ") is unparsable.");
            System.exit(-1);
        }

        // gets the block hashspecified.
        String strBlockHash = cmd.getOptionValue("b");
        try{
            blockHash = Sha256Hash.wrap(strBlockHash);
        } catch (Exception e){
            System.err.println("The provided block Hash (" + strBlockHash + ") is unparsable.");
            System.exit(-1);
        }

        // we have everything we need. Let's redeem!
        redeemTransaction = new RedeemTransaction(privateKey, redeemScript, transactionHash, blockHash);
        try {
            Transaction transaction = redeemTransaction.generateTransaction();
            redeemTransaction.broadcastTransaction(transaction);

        } catch (BlockStoreException e) {
            e.printStackTrace();
        } catch (TransactionErrorException e) {
            e.printStackTrace();
        } catch (CantConnectToFermatBlockchainException e) {
            e.printStackTrace();
        }
    }


    /**
     * adds all the options
     * @return
     */
    private static Options defineOptions(){
        Options options = new Options();
        Option optPrivKey = new Option("p", "privateKey", true, "Private Key for PreMined Transaction funds.");
        optPrivKey.setRequired(false);
        options.addOption(optPrivKey);

        Option redeemScript = new Option("r", "redeemScript", true, "Your Redeem script hexadecimal code");
        optPrivKey.setRequired(false);
        options.addOption(redeemScript);

        Option genesisTransaction = new Option("t", "transactionHash", true, "The hash of your premined transaction.");
        optPrivKey.setRequired(false);
        options.addOption(genesisTransaction);

        Option genesisBlock = new Option("b", "BlockHash", true, "The hash of the block that included your premined transaction.");
        optPrivKey.setRequired(false);
        options.addOption(genesisBlock);

        Option optNetwork = new Option("n", "network", true, "Fermat Network to connecto to: MAIN, TEST or REGTEST. Default is MAIN.");
        optNetwork.setRequired(false);
        options.addOption(optNetwork);

        Option optHelp = new Option("h", "help", false, "shows this Help");
        optHelp.setRequired(false);
        options.addOption(optHelp);


        Option optDebug = new Option("d", "debug", false, "shows debug information");
        optDebug.setRequired(false);
        options.addOption(optDebug);


        return options;
    }

    /**
     * validates if the passed key is a correct private key
     * @param pKey the string private key to validate.
     * @return true if is valid on the current network
     */
    private static boolean isPrivateKeyValid(String pKey) {
        try {
            DumpedPrivateKey dumpedPrivateKey = new DumpedPrivateKey(networkParameters, pKey);
            ECKey key = dumpedPrivateKey.getKey();
            if (key.isPubKeyOnly())
                return false;

            privateKey = key;
        } catch (AddressFormatException e) {
            return false;
        }

        return true;
    }

    /**
     * parse mandatory parameters
     * @return
     */
    private static boolean isMandatoryArguments() {
        if (cmd.hasOption("h"))
            return true;


        if (cmd.hasOption("p") && cmd.hasOption("r") && cmd.hasOption("t") && cmd.hasOption("b"))
            return true;
        else
            return false;
    }

    private static void defineNetwork() {
        if (cmd.hasOption("n")){
            switch (cmd.getOptionValue("n").toUpperCase()){
                case "MAIN":
                    networkParameters = MainNetParams.get();
                    break;
                case "TEST":
                    networkParameters = TestNet3Params.get();
                    break;
                case "REGTEST":
                    networkParameters = RegTestParams.get();
                    break;
                default:
                    formatter.printHelp("Invalid Network parameter specified.", options);

                    System.exit(1);
                    return;
            }
        } else
            networkParameters = MainNetParams.get();

    }
}
