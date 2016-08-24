package org.sleuthkit.autopsy.datamodel;

import java.util.Optional;

/**
 * Representation of a range of Issuer/Bank Identifiaction Numbers (IIN/BIN) for
 * the * same bank.
 */
public class IINRange {

    private final int IINStart;
    private final int IINEnd;
    private final Integer numberLength;
    /**
     * AMEX, VISA, MASTERCARD, DINERS, DISCOVER, UNIONPAY
     */
    private final String scheme;
    private final String brand;
    /**
     * DEBIT, CREDIT
     */
    private final String type;

    private final String country;
    private final String bankName;
    private final String bankURL;
    private final String bankPhoneNumber;
    private final String bankCity;

    IINRange(int IIN_start, int IIN_end, Integer number_length, String scheme, String brand, String type, String country, String bank_name, String bank_url, String bank_phone, String bank_city) {
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

    public Optional<Integer> getNumber_length() {
        return Optional.ofNullable(numberLength);
    }

    public String getScheme() {
        return scheme;
    }

    public String getBrand() {
        return brand;
    }

    public String getCardType() {
        return type;
    }

    public String getCountry() {
        return country;
    }

    public String getBankName() {
        return bankName;
    }

    public String getBankURL() {
        return bankURL;
    }

    public String getBankPhoneNumber() {
        return bankPhoneNumber;
    }

    public String getBankCity() {
        return bankCity;
    }
}
