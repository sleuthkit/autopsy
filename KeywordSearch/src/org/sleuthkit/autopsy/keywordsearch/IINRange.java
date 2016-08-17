package org.sleuthkit.autopsy.keywordsearch;

/**
 * Representation of a range of Issuer Identifiaction Numbers for the same bank.
 */
public class IINRange {

    private final int IIN_start;
    private final int IIN_end;
    private final int number_length;
    private final CreditCardScheme scheme;
    private final String brand;
    private final PaymentCardType type;

    private final String country;
    private final String bank_name;
    private final String bank_logo;
    private final String bank_url;
    private final String bank_phone;
    private final String bank_city;

    enum PaymentCardType {
        DEBIT, DREDIT;
    }

    enum CreditCardScheme {
        AMEX, VISA, MASTERCARD, DINERS, DISCOVER
    }

    IINRange(int IIN_start, int IIN_end, int number_length, CreditCardScheme scheme, String brand, PaymentCardType type, String country, String bank_name, String bank_logo, String bank_url, String bank_phone, String bank_city) {
        this.IIN_start = IIN_start;
        this.IIN_end = IIN_end;
        this.number_length = number_length;
        this.scheme = scheme;
        this.brand = brand;
        this.type = type;
        this.country = country;
        this.bank_name = bank_name;
        this.bank_logo = bank_logo;
        this.bank_url = bank_url;
        this.bank_phone = bank_phone;
        this.bank_city = bank_city;
    }

    public int getIIN_start() {
        return IIN_start;
    }

    public int getIIN_end() {
        return IIN_end;
    }

    public int getNumber_length() {
        return number_length;
    }

    public CreditCardScheme getScheme() {
        return scheme;
    }

    public String getBrand() {
        return brand;
    }

    public PaymentCardType getType() {
        return type;
    }

    public String getCountry() {
        return country;
    }

    public String getBank_name() {
        return bank_name;
    }

    public String getBank_logo() {
        return bank_logo;
    }

    public String getBank_url() {
        return bank_url;
    }

    public String getBank_phone() {
        return bank_phone;
    }

    public String getBank_city() {
        return bank_city;
    }

}
