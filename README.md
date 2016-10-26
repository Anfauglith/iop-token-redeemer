# IoP Token Redeemer

Redeems tokens that where sent with a time locked constraint as part of the premining distribution phase.

### Usage

Execute .jar app with -h for help on the parameters
```
$ java -jar FermatPreminingRedeemer.jar  -h
```


```
usage: Help
 -b,--BlockHash <arg>         The hash of the block that included your
                              premined transaction.
 -d,--debug                   shows debug information
 -h,--help                    shows this Help
 -n,--network <arg>           Fermat Network to connecto to: MAIN, TEST or
                              REGTEST. Default is MAIN.
 -p,--privateKey <arg>        Private Key for PreMined Transaction funds.
 -r,--redeemScript <arg>      Your Redeem script hexadecimal code
 -t,--transactionHash <arg>   The hash of your premined transaction.

```

Mandatory arguments are:

* **-BlockHash**: The block that contains the *Genesis* transaction that sent you the time locked tokens.
  
* **-transactionHash**: the hash of the *Genesis* transaction that includes the time locked tokens sent to you.

* **-redeemScript**:  the redeem script in hexadecimal code that will be use in the ScriptSig of the new transaction that will redeem the tokens sent to you.

These arguments can be found at https://docs.google.com/spreadsheets/d/1CtEgBS87alyNm51bhboH5HOAVyhupWVaU6KvsPVgNbo/edit?usp=sharing

The *private  key* argument is the only one you need to specify on your own.

* **-privateKey**:  the private key of the address that you specified in the Token spreadsheet. You can generate this private key by executing: **dumpprivkey [yourAddress]** from your IoP wallet.


  
  
Default network is Mainnet. To switch, use the -network parameter. Example:

```
FermatPreminingRedeemer.jar -r 044c700f58b17576a914b856013eb1a532c37802fd2201eddbd386e1848288ac -t a835c82ad4708323a5272e96ca03e213af0dd5771d114b3ea633ff5c26b9dfe7 -b 00000000c966f3f962a54e94618795d8664d5271607b669dbab659d031f9c6eb -p CNBHtzZ8BfFx82FYxrskrcCwUX3YF1Qi9TrgUuhGMHKCVkU3XCzy -n test
```

## Program description

During the Premining distribution phase, tokens **available now** where sent according to the **Fermat Tokens** spreadsheet.

Aproximatelly 25% of the total tokens to be distributed are time locked constrained. Meaning that they are only redeemable at some point in the future.

Transactions are time constrained with the following dates:

* 6 Months 
* 1 year
* 2 years
* 3 years
* 4 years
* 5 years

These transactions are Pay-2-Script-Hash (P2SH) transtractions with the following output type:

```
OP_HASH160 bfc1850c041f24d309b687043eaf5f81b16e9f00 OP_EQUAL
```

And in more detail, the redeem script has the following form:

```
1477437014 OP_CHECKLOCKTIMEVERIFY OP_DROP OP_DUP OP_HASH160 3b92407fb8d5aa76c1b12cf836d12f69c47e688e OP_EQUALVERIFY OP_CHECKSIG
```

The transaction used OP_CHECKLOCKTIMEVERIFY as the first part of the puzzle to solve, then a publick key that hashes to the script address and finally a valid signature for the transaction.

This program is used to generate the transaction that solves the scriptSig script by pushing into the stack:

1) **Signature** generated with the private key you specified.
2) **Public Key** obtained from the private key you specified.
3) **Redeem Script** obtained from the parameter you specified.





## Execution

The transaction generated to redeem the time locked tokens is a standard transaction.

The nTimeLock execution, as in bitcoin is validated against the blocks and times stamped on the blockchain.

The program validates that the transaction you are trying to submit will be valid on the network and is considered **Final**. If the transaction is not yet final, meaning you haven't reached the date of the time constraint, it will show an error.

There might be times that the transaction is submited succesfully into the blockchain but the IoPs are not corretly redeemed. Not all peers will inform back if the transaction is not yet final, so you may need to execute the program multiple times even if you had a sucessfull broadcasting message.



## Author

* **Rodrigo Acosta**  - [acostarodrigo](https://github.com/acostarodrigo)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details
