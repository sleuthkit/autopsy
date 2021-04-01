/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.recentactivity;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizer;
import org.sleuthkit.autopsy.url.analytics.DomainCategorizerException;
import org.sleuthkit.autopsy.url.analytics.DomainCategory;

/**
 * The autopsy provided domain category provider that overrides all domain
 * category providers except the custom web domain categorizations.
 */
@Messages({
    "DefaultPriorityDomainCategorizer_searchEngineCategory=Search Engine"
})
public class DefaultPriorityDomainCategorizer implements DomainCategorizer {

    // taken from https://www.google.com/supported_domains
    private static final List<String> GOOGLE_DOMAINS = Arrays.asList("google.com", "google.ad", "google.ae", "google.com.af", "google.com.ag", "google.com.ai", "google.al", "google.am", "google.co.ao", "google.com.ar", "google.as", "google.at", "google.com.au", "google.az", "google.ba", "google.com.bd", "google.be", "google.bf", "google.bg", "google.com.bh", "google.bi", "google.bj", "google.com.bn", "google.com.bo", "google.com.br", "google.bs", "google.bt", "google.co.bw", "google.by", "google.com.bz", "google.ca", "google.cd", "google.cf", "google.cg", "google.ch", "google.ci", "google.co.ck", "google.cl", "google.cm", "google.cn", "google.com.co", "google.co.cr", "google.com.cu", "google.cv", "google.com.cy", "google.cz", "google.de", "google.dj", "google.dk", "google.dm", "google.com.do", "google.dz", "google.com.ec", "google.ee", "google.com.eg", "google.es", "google.com.et", "google.fi", "google.com.fj", "google.fm", "google.fr", "google.ga", "google.ge", "google.gg", "google.com.gh", "google.com.gi", "google.gl", "google.gm", "google.gr", "google.com.gt", "google.gy", "google.com.hk", "google.hn", "google.hr", "google.ht", "google.hu", "google.co.id", "google.ie", "google.co.il", "google.im", "google.co.in", "google.iq", "google.is", "google.it", "google.je", "google.com.jm", "google.jo", "google.co.jp", "google.co.ke", "google.com.kh", "google.ki", "google.kg", "google.co.kr", "google.com.kw", "google.kz", "google.la", "google.com.lb", "google.li", "google.lk", "google.co.ls", "google.lt", "google.lu", "google.lv", "google.com.ly", "google.co.ma", "google.md", "google.me", "google.mg", "google.mk", "google.ml", "google.com.mm", "google.mn", "google.ms", "google.com.mt", "google.mu", "google.mv", "google.mw", "google.com.mx", "google.com.my", "google.co.mz", "google.com.na", "google.com.ng", "google.com.ni", "google.ne", "google.nl", "google.no", "google.com.np", "google.nr", "google.nu", "google.co.nz", "google.com.om", "google.com.pa", "google.com.pe", "google.com.pg", "google.com.ph", "google.com.pk", "google.pl", "google.pn", "google.com.pr", "google.ps", "google.pt", "google.com.py", "google.com.qa", "google.ro", "google.ru", "google.rw", "google.com.sa", "google.com.sb", "google.sc", "google.se", "google.com.sg", "google.sh", "google.si", "google.sk", "google.com.sl", "google.sn", "google.so", "google.sm", "google.sr", "google.st", "google.com.sv", "google.td", "google.tg", "google.co.th", "google.com.tj", "google.tl", "google.tm", "google.tn", "google.to", "google.com.tr", "google.tt", "google.com.tw", "google.co.tz", "google.com.ua", "google.co.ug", "google.co.uk", "google.com.uy", "google.co.uz", "google.com.vc", "google.co.ve", "google.vg", "google.co.vi", "google.com.vn", "google.vu", "google.ws", "google.rs", "google.co.za", "google.co.zm", "google.co.zw", "google.cat");

    // taken from https://www.yahoo.com/everything/world
    private static final List<String> YAHOO_DOMAINS = Arrays.asList("espanol.yahoo.com", "au.yahoo.com", "be.yahoo.com", "fr-be.yahoo.com", "br.yahoo.com", "ca.yahoo.com", "espanol.yahoo.com", "espanol.yahoo.com", "de.yahoo.com", "es.yahoo.com", "espanol.yahoo.com", "fr.yahoo.com", "in.yahoo.com", "id.yahoo.com", "ie.yahoo.com", "it.yahoo.com", "en-maktoob.yahoo.com", "malaysia.yahoo.com", "espanol.yahoo.com", "nz.yahoo.com", "espanol.yahoo.com", "ph.yahoo.com", "qc.yahoo.com", "ro.yahoo.com", "sg.yahoo.com", "za.yahoo.com", "se.yahoo.com", "uk.yahoo.com", "yahoo.com", "espanol.yahoo.com", "vn.yahoo.com", "gr.yahoo.com", "maktoob.yahoo.com", "yahoo.com", "hk.yahoo.com", "tw.yahoo.com", "yahoo.co.jp");

    private static final List<String> OTHER_SEARCH_ENGINES = Arrays.asList(
            "bing.com",
            "baidu.com",
            "sogou.com",
            "soso.com",
            "duckduckgo.com",
            "swisscows.com",
            "gibiru.com",
            "cutestat.com",
            "youdao.com",
            "biglobe.ne.jp",
            "givewater.com",
            "ekoru.org",
            "ecosia.org",
            // according to https://en.wikipedia.org/wiki/Yandex
            "yandex.ru",
            "yandex.com"
    );

    private static final String WWW_PREFIX = "www";

    private static final Map<String, String> DOMAIN_LOOKUP
            = Stream.of(GOOGLE_DOMAINS, YAHOO_DOMAINS, OTHER_SEARCH_ENGINES)
                    .flatMap((lst) -> lst.stream())
                    .collect(Collectors.toMap((k) -> k, (k) -> Bundle.DefaultPriorityDomainCategorizer_searchEngineCategory(), (v1, v2) -> v1));

    @Override
    public void initialize() throws DomainCategorizerException {
    }

    @Override
    public DomainCategory getCategory(String domain, String host) throws DomainCategorizerException {

        String hostToUse = StringUtils.isBlank(host) ? domain : host;

        if (StringUtils.isBlank(hostToUse)) {
            return null;
        }

        List<String> domainWords = Stream.of(hostToUse.toLowerCase().split("\\."))
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());

        String sanitizedDomain = domainWords.stream()
                // skip first word segment if 'www'
                .skip(domainWords.size() > 0 && WWW_PREFIX.equals(domainWords.get(0)) ? 1 : 0)
                .collect(Collectors.joining("."));

        String category = DOMAIN_LOOKUP.get(sanitizedDomain);
        return category == null ? null : new DomainCategory(sanitizedDomain, category);
    }

    @Override
    public void close() throws IOException {
    }
}
