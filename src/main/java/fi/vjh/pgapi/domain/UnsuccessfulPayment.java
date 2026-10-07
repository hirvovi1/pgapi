package fi.vjh.pgapi.domain;

public class UnsuccessfulPayment extends Exception {
    public UnsuccessfulPayment(String message) {
        super(message);
    }

    public UnsuccessfulPayment(String message, Throwable cause) {
        super(message, cause);
    }
}
