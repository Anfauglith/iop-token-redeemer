package org.fermat;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.fermatj.core.*;
import org.fermatj.crypto.TransactionSignature;
import org.fermatj.net.discovery.DnsDiscovery;
import org.fermatj.params.MainNetParams;
import org.fermatj.params.RegTestParams;
import org.fermatj.params.TestNet3Params;
import org.fermatj.script.Script;
import org.fermatj.script.ScriptBuilder;
import org.fermatj.script.ScriptChunk;
import org.fermatj.store.BlockStore;
import org.fermatj.store.BlockStoreException;
import org.fermatj.store.MemoryBlockStore;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.crypto.signers.HMacDSAKCalculator;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;


import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by rodrigo on 8/16/16.
 */
public class RedeemTransaction {
    // class final variables
    private final ECKey privateKey;
    private final Script redeemScript;
    private final Sha256Hash transactionHash;
    private final Sha256Hash blockHash;
    private final NetworkParameters NETWORK;

    //class variables
    private Transaction transaction;
    private Transaction genesisTransaction;
    private Context context;
    private BlockChain blockChain;

    private PeerGroup peerGroup;

    /**
     * constructor
     * @param redeemScript
     */
    public RedeemTransaction(ECKey privateKey, Script redeemScript, Sha256Hash transactionHash, Sha256Hash blockHash) {
        Preconditions.checkNotNull(privateKey);
        Preconditions.checkNotNull(redeemScript);
        Preconditions.checkNotNull(transactionHash);
        Preconditions.checkNotNull(blockHash);

        this.privateKey = privateKey;
        this.redeemScript = redeemScript;
        this.transactionHash = transactionHash;
        this.blockHash = blockHash;

        this.NETWORK = Main.getNetworkParameters();
    }

    public void broadcastTransaction(Transaction transaction) throws TransactionErrorException {
        System.out.println("Broadcasting transaction " + transaction.getHashAsString() + " ...");
        try {
            final TransactionBroadcast transactionBroadcast = peerGroup.broadcastTransaction(transaction);
            transactionBroadcast.setMinConnections(2);

            final ListenableFuture<Transaction> future = transactionBroadcast.broadcast();

            Futures.addCallback(future, new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            System.out.println("Transaction broadcasted sucessfully");
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            System.err.println(throwable.toString());
                            System.exit(-1);
                        }
                    }
            );
        } catch (Exception e) {
            throw new TransactionErrorException("There was a problem broadcasting the passed transaction. " + transaction.toString(), e);
        }


    }

    /**
     * Generates the transaction that pays to the passed address using the redeemScript
     */
    public Transaction generateTransaction () throws CantConnectToFermatBlockchainException, TransactionErrorException, BlockStoreException {
        // connect to the network
        connect();

        genesisTransaction = getGenesisTransaction(transactionHash, blockHash);

        if (genesisTransaction == null)
            throw new RuntimeException("We couldn't get the genesis transaction. Can't go on.");

        transaction = new Transaction(Main.getNetworkParameters());

        // we get the genesis output. From where the coins are going to be
        TransactionOutput genesisOutput = getGenesisTransactionOutput(genesisTransaction);
        if (genesisOutput == null)
            throw new TransactionErrorException("We couldn't find a matching redeem script on genesis transaction.");


        transaction.addInput(genesisOutput);

        // add the output with all the original genesis value - fee
        Coin value = genesisOutput.getValue().subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
        transaction.addOutput(value, privateKey);

        //set transaction purpose.
        transaction.setPurpose(Transaction.Purpose.USER_PAYMENT);

        // push de original redeem script into the stack
        ScriptChunk chunkRedeemProgram = new ScriptChunk(redeemScript.getProgram().length, redeemScript.getProgram());

        //push the original redeem script locktime specified.
        long epochTime = getEpochTime(redeemScript);
        if (epochTime == 0)
            throw new TransactionErrorException("Can't get original redeem script locktime. Can't go on.");

        // I need to define the sequence number other than the max to enable locktime.
        transaction.getInput(0).setSequenceNumber(0);

        //set transaction lock time. needed for NOP02
        transaction.setLockTime(epochTime);

        // let's make sure this transaction is final before continuing.
        long prevBlockTime = getBlockTime();
        if (!transaction.isFinal(peerGroup.getMostCommonChainHeight(), prevBlockTime)){
            Date dateConstraint = new Date(transaction.getLockTime()*1000L);
            throw new TransactionErrorException("The Redeem Script was created with a date in the future. Wait until " + dateConstraint.toString() + " and try again");
        }


        Sha256Hash signHash = transaction.hashForSignature(0, redeemScript, Transaction.SigHash.ALL, false);



        // then the signature
        TransactionSignature signature = transaction.calculateSignature(0, privateKey, redeemScript, Transaction.SigHash.ALL, false);
        ScriptChunk chunkSignature = new ScriptChunk(signature.encodeToFermat().length, signature.encodeToFermat());


        ScriptChunk publicKey = new ScriptChunk(privateKey.getPubKey().length, privateKey.getPubKey());

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        scriptBuilder.addChunk(chunkSignature);
        scriptBuilder.addChunk(publicKey);
        scriptBuilder.addChunk(chunkRedeemProgram);


        Script scriptSig = scriptBuilder.build();

        transaction.getInput(0).setScriptSig(scriptSig);

        System.out.println(Hex.toHexString(transaction.fermatSerialize()));

        try{
            //we verify we can spend it
            transaction.getInput(0).verify();
            transaction.verify();
        } catch (Exception e){
            throw new TransactionErrorException("There was an error verifying the generated transaction.\n " + e.getMessage());
        }


        return transaction;
    }

    /**
     * gets the locktime from the redeem script-
     * The lock time is the first push data chunk in Hexadecimal
     * @param redeemScript
     * @return
     */
    private long getEpochTime(Script redeemScript) {
        try {
            ScriptChunk chunk = redeemScript.getChunks().get(0);
            if (!chunk.isPushData())
                return 0;

            ByteBuffer bb = ByteBuffer.wrap(chunk.data);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            return bb.getInt();
        }catch (Exception e){
            return 0;
        }
    }

    private Transaction getGenesisTransaction(Sha256Hash transactionHash, Sha256Hash blockHash) {
        Block genesisBlock = null;
        try {
            genesisBlock = peerGroup.getDownloadPeer().getBlock(blockHash).get(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't get Genesis block from miner.");
        }

        for (Transaction transaction : genesisBlock.getTransactions()){
            if (transaction.getHash().equals(transactionHash))
                return transaction;
        }
        return null;
    }


    /**
     * finds a matching output that cointains the same redeem script.
     * @param genesisTransaction
     * @return
     */
    private TransactionOutput getGenesisTransactionOutput(Transaction genesisTransaction) {
        Preconditions.checkNotNull(genesisTransaction);

        Script p2SH = ScriptBuilder.createP2SHOutputScript(redeemScript);
        Address redeemScriptAddress = Address.fromP2SHScript(NETWORK, p2SH);

        for (TransactionOutput output : genesisTransaction.getOutputs()){
            if (output.getScriptPubKey().isPayToScriptHash()){
                Address genesisP2SHAddress = Address.fromP2SHScript(NETWORK, output.getScriptPubKey());

                if (redeemScriptAddress.equals(genesisP2SHAddress))
                    return output;

            }
        }
        return null;
    }

    private void connect() throws BlockStoreException {
        context = Context.getOrCreate(NETWORK);
        BlockStore blockStore = new MemoryBlockStore(NETWORK);
        blockChain = new BlockChain(NETWORK, blockStore);

        peerGroup = new PeerGroup(NETWORK, blockChain);
        if (NETWORK.equals(RegTestParams.get())){
            peerGroup.setUseLocalhostPeerWhenPossible(true);
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("127.0.0.1", 14820)));
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("127.0.0.1", 14821)));
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("127.0.0.1", 14822)));

            peerGroup.setMinBroadcastConnections(2);
        }
        if (NETWORK.equals(TestNet3Params.get())){
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.199.219.45", 7475)));       //ham4
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.196.57.34", 7475)));        //ham5
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.199.118.223", 7475)));      //ham6
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.196.161.16", 7475)));       //ham7
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.199.194.195", 7475)));      //ham8
        }
        if (NETWORK.equals(MainNetParams.get())){
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.155.51.239", 4877)));       //ham1
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.199.126.235", 4877)));      //ham2
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("130.211.120.237", 4877)));      //ham3
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.199.219.45", 4877)));       //ham4
            peerGroup.addAddress(new PeerAddress(new InetSocketAddress("104.196.57.34", 4877)));        //ham5
        }

        peerGroup.start();
        peerGroup.downloadBlockChain();

    }

    public long getBlockTime() throws BlockStoreException {
        return blockChain.getChainHead().getPrev(blockChain.getBlockStore()).getHeader().getTimeSeconds();
    }
}
