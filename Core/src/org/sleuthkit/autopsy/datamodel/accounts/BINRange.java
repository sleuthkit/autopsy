/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datamodel.accounts;

import java.util.Optional;
import javax.annotation.concurrent.Immutable;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.datamodel.CreditCards;

/**
 * Details of a range of Bank Identification Number(s) (BIN) used by a bank.
 */
@Immutable
public class BINRange implements CreditCards.BankIdentificationNumber {

    private final int BINStart; //start of BIN range, 8 digits
    private final int BINEnd; // end (incluse ) of BIN rnage, 8 digits

    private final Integer numberLength; // the length of accounts numbers with this BIN, currently unused

    /**
     * AMEX, VISA, MASTERCARD, DINERS, DISCOVER, UNIONPAY
     */
    private final String scheme;
    private final String brand;

    /**
     * DEBIT, CREDIT
     */
    private final String cardType;
    private final String country;
    private final String bankName;
    private final String bankCity;
    private final String bankURL;
    private final String bankPhoneNumber;

    /**
     * Constructor
     *
     * @param BIN_start     the first BIN in the range, must be 8 digits
     * @param BIN_end       the last(inclusive) BIN in the range, must be 8
     *                      digits
     * @param number_length the length of account numbers in this BIN range
     * @param scheme        amex/visa/mastercard/etc
     * @param brand         the brand of this BIN range
     * @param type          credit vs debit
     * @param country       the country of the issuer
     * @param bank_name     the name of the issuer
     * @param bank_url      the url of the issuer
     * @param bank_phone    the phone number of the issuer
     * @param bank_city     the city of the issuer
     */
    public BINRange(int BIN_start, int BIN_end, Integer number_length, String scheme, String brand, String type, String country, String bank_name, String bank_url, String bank_phone, String bank_city) {
        this.BINStart = BIN_start;
        this.BINEnd = BIN_end;

        this.numberLength = number_length;
        this.scheme = StringUtils.defaultIfBlank(scheme, null);
        this.brand = StringUtils.defaultIfBlank(brand, null);
        this.cardType = StringUtils.defaultIfBlank(type, null);
        this.country = StringUtils.defaultIfBlank(country, null);
        this.bankName = StringUtils.defaultIfBlank(bank_name, null);
        this.bankURL = StringUtils.defaultIfBlank(bank_url, null);
        this.bankPhoneNumber = StringUtils.defaultIfBlank(bank_phone, null);
        this.bankCity = StringUtils.defaultIfBlank(bank_city, null);
    }

    /**
     * Get the first BIN in this range
     *
     * @return the first BIN in this range.
     */
    public int getBINstart() {
        return BINStart;
    }

    /**
     * Get the last (inclusive) BIN in this range.
     *
     * @return the last (inclusive) BIN in this range.
     */
    public int getBINend() {
        return BINEnd;
    }

    @Override
    public Optional<Integer> getNumberLength() {
        return Optional.ofNullable(numberLength);
    }

    @Override
    public Optional<String> getScheme() {
        return Optional.ofNullable(scheme);
    }

    @Override
    public Optional<String> getBrand() {
        return Optional.ofNullable(brand);
    }

    @Override
    public Optional<String> getCardType() {
        return Optional.ofNullable(cardType);
    }

    @Override
    public Optional<String> getCountry() {
        return Optional.ofNullable(country);
    }

    @Override
    public Optional<String> getBankName() {
        return Optional.ofNullable(bankName);
    }

    @Override
    public Optional<String> getBankURL() {
        return Optional.ofNullable(bankURL);
    }

    @Override
    public Optional<String> getBankPhoneNumber() {
        return Optional.ofNullable(bankPhoneNumber);
    }

    @Override
    public Optional<String> getBankCity() {
        return Optional.ofNullable(bankCity);
    }
}
