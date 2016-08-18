package org.sleuthkit.autopsy.keywordsearch;

/**
 * Representation of a range of Issuer/Bank Identifiaction Numbers (IIN/BIN) for
 * the * same bank.
 */
class IINRange {

    private final int IINStart;
    private final int IINEnd;
    private final int numberLength;
    private final PaymentCardScheme scheme;
    private final String brand;
    private final PaymentCardType type;

    private final String country;
    private final String bankName;
    private final String bankURL;
    private final String bankPhoneNumber;
    private final String bankCity;

    enum PaymentCardType {
        DEBIT, DREDIT;
    }

    enum PaymentCardScheme {
        AMEX, VISA, MASTERCARD, DINERS, DISCOVER
    }

    IINRange(int IIN_start, int IIN_end, int number_length, PaymentCardScheme scheme, String brand, PaymentCardType type, String country, String bank_name, String bank_url, String bank_phone, String bank_city) {
        this.IINStart = IIN_start;
        this.IINEnd = IIN_end;
        this.numberLength = number_length;
        this.scheme = scheme;
        this.brand = brand;
        this.type = type;
        this.country = country;
        this.bankName = bank_name;

        this.bankURL = bank_url;
        this.bankPhoneNumber = bank_phone;
        this.bankCity = bank_city;
    }

    int getIINstart() {
        return IINStart;
    }

    int getIINend() {
        return IINEnd;
    }

    int getNumber_length() {
        return numberLength;
    }

    PaymentCardScheme getPaymentCardScheme() {
        return scheme;
    }

    String getBrand() {
        return brand;
    }

    PaymentCardType getPaymentCardType() {
        return type;
    }

    String getCountry() {
        return country;
    }

    String getBankName() {
        return bankName;
    }

    String getBankURL() {
        return bankURL;
    }

    String getBankPhoneNumber() {
        return bankPhoneNumber;
    }

    String getBankCity() {
        return bankCity;
    }

}
