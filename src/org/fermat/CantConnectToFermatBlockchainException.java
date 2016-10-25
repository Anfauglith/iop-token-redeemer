package org.fermat;

/**
 * Created by rodrigo on 7/22/16.
 */
public class CantConnectToFermatBlockchainException extends Exception {
    public CantConnectToFermatBlockchainException(String message, Throwable cause) {
        super(message, cause);
    }

    public CantConnectToFermatBlockchainException(String message) {
        super(message);
    }
}
