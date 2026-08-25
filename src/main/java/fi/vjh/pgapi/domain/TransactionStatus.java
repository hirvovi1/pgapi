package fi.vjh.pgapi.domain;

public enum TransactionStatus {
    PENDING,    // Otettu vastaan, odottaa käsittelyä jonossa
    SUCCESS,    // Rahansiirto suoritettu onnistuneesti
    FAILED      // Siirto epäonnistunut (esim. katteen puute tai virheellinen tili)
}
