#-----------------------------------------------------------
# nation.pl
# Region Information
# Get Geo Nation information from the NTUSER.DAT hive file
#
# Written By:
# Fahad Alzaabi
# falzaab@masonlive.gmu.edu
# George Mason University,CFRS 763 
#-----------------------------------------------------------
package nation;
use strict;

my %config = (hive          => "ntuser.dat",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091116);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets region information from HKCU";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching nation v.".$VERSION);
	::rptMsg("nation v.".$VERSION);
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Control Panel\\International\\Geo";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Nation Information Check");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $nation = $key->get_value("Nation")->get_data();
		::rptMsg("The Region value is : ".$nation);
		::rptMsg("The Country Is: Antigua and Barbuda") if ($nation == 2);
		::rptMsg("The Country Is: Afghanistan") if ($nation == 3);
		::rptMsg("The Country Is: Algeria") if ($nation == 4);
		::rptMsg("The Country Is: Azerbaijan") if ($nation == 5);
		::rptMsg("The Country Is: Albania") if ($nation == 6);
		::rptMsg("The Country Is: Armenia") if ($nation == 7);
		::rptMsg("The Country Is: Andorra") if ($nation == 8);
		::rptMsg("The Country Is: Angola") if ($nation == 9);
		::rptMsg("The Country Is: American Samoa") if ($nation == 10);
		::rptMsg("The Country Is: Argentina") if ($nation == 11);
		::rptMsg("The Country Is: Australia") if ($nation == 12);
		::rptMsg("The Country Is: Austria") if ($nation == 14);
		::rptMsg("The Country Is: Bahrain") if ($nation == 17);
		::rptMsg("The Country Is: Barbados") if ($nation == 18);
		::rptMsg("The Country Is: Botswana") if ($nation == 19);
		::rptMsg("The Country Is: Bermuda") if ($nation == 20);
		::rptMsg("The Country Is: Belgium") if ($nation == 21);
		::rptMsg("The Country Is: Bahamas The") if ($nation == 22);
		::rptMsg("The Country Is: Bangladesh") if ($nation == 23);
		::rptMsg("The Country Is: Belize") if ($nation == 24);
		::rptMsg("The Country Is: Bosnia and Herzegovina") if ($nation == 25);
		::rptMsg("The Country Is: Bolivia") if ($nation == 26);
		::rptMsg("The Country Is: Myanmar") if ($nation == 27);
		::rptMsg("The Country Is: Benin") if ($nation == 28);
		::rptMsg("The Country Is: Belarus") if ($nation == 29);
		::rptMsg("The Country Is: Solomon Islands") if ($nation == 30);
		::rptMsg("The Country Is: Brazil") if ($nation == 32);
		::rptMsg("The Country Is: Bhutan") if ($nation == 34);
		::rptMsg("The Country Is: Bulgaria") if ($nation == 35);
		::rptMsg("The Country Is: Brunei") if ($nation == 37);
		::rptMsg("The Country Is: Burundi") if ($nation == 38);
		::rptMsg("The Country Is: Canada") if ($nation == 39);
		::rptMsg("The Country Is: Cambodia") if ($nation == 40);
		::rptMsg("The Country Is: Chad") if ($nation == 41);
		::rptMsg("The Country Is: Sri Lanka") if ($nation == 42);
		::rptMsg("The Country Is: Congo") if ($nation == 43);
		::rptMsg("The Country Is: Congo (DRC)") if ($nation == 44);
		::rptMsg("The Country Is: China") if ($nation == 45);
		::rptMsg("The Country Is: Chile") if ($nation == 46);
		::rptMsg("The Country Is: Cameroon") if ($nation == 49);
		::rptMsg("The Country Is: Comoros") if ($nation == 50);
		::rptMsg("The Country Is: Colombia") if ($nation == 51);
		::rptMsg("The Country Is: Costa Rica") if ($nation == 54);
		::rptMsg("The Country Is: Central African Republic") if ($nation == 55);
		::rptMsg("The Country Is: Cuba") if ($nation == 56);
		::rptMsg("The Country Is: Cabo Verde") if ($nation == 57);
		::rptMsg("The Country Is: Cyprus") if ($nation == 59);
		::rptMsg("The Country Is: Denmark") if ($nation == 61);
		::rptMsg("The Country Is: Djibouti") if ($nation == 62);
		::rptMsg("The Country Is: Dominica") if ($nation == 63);
		::rptMsg("The Country Is: Dominican Republic") if ($nation == 65);
		::rptMsg("The Country Is: Ecuador") if ($nation == 66);
		::rptMsg("The Country Is: Egypt") if ($nation == 67);
		::rptMsg("The Country Is: Ireland") if ($nation == 68);
		::rptMsg("The Country Is: Equatorial Guinea") if ($nation == 69);
		::rptMsg("The Country Is: Estonia") if ($nation == 70);
		::rptMsg("The Country Is: Eritrea") if ($nation == 71);
		::rptMsg("The Country Is: El Salvador") if ($nation == 72);
		::rptMsg("The Country Is: Ethiopia") if ($nation == 73);
		::rptMsg("The Country Is: Czech Republic") if ($nation == 75);
		::rptMsg("The Country Is: Finland") if ($nation == 77);
		::rptMsg("The Country Is: Fiji") if ($nation == 78);
		::rptMsg("The Country Is: Micronesia") if ($nation == 80);
		::rptMsg("The Country Is: Faroe Islands") if ($nation == 81);
		::rptMsg("The Country Is: France") if ($nation == 84);
		::rptMsg("The Country Is: Gambia") if ($nation == 86);
		::rptMsg("The Country Is: Gabon") if ($nation == 87);
		::rptMsg("The Country Is: Georgia") if ($nation == 88);
		::rptMsg("The Country Is: Ghana") if ($nation == 89);
		::rptMsg("The Country Is: Gibraltar") if ($nation == 90);
		::rptMsg("The Country Is: Grenada") if ($nation == 91);
		::rptMsg("The Country Is: Greenland") if ($nation == 93);
		::rptMsg("The Country Is: Germany") if ($nation == 94);
		::rptMsg("The Country Is: Greece") if ($nation == 98);
		::rptMsg("The Country Is: Guatemala") if ($nation == 99);
		::rptMsg("The Country Is: Guinea") if ($nation == 100);
		::rptMsg("The Country Is: Guyana") if ($nation == 101);
		::rptMsg("The Country Is: Haiti") if ($nation == 103);
		::rptMsg("The Country Is: Hong Kong ") if ($nation == 104);
		::rptMsg("The Country Is: Honduras") if ($nation == 106);
		::rptMsg("The Country Is: Croatia") if ($nation == 108);
		::rptMsg("The Country Is: Hungary") if ($nation == 109);
		::rptMsg("The Country Is: Iceland") if ($nation == 110);
		::rptMsg("The Country Is: Indonesia") if ($nation == 111);
		::rptMsg("The Country Is: India") if ($nation == 113);
		::rptMsg("The Country Is: British Indian Ocean Territory") if ($nation == 114);
		::rptMsg("The Country Is: Iran") if ($nation == 116);
		::rptMsg("The Country Is: Israel") if ($nation == 117);
		::rptMsg("The Country Is: Italy") if ($nation == 118);
		::rptMsg("The Country Is: Côte dIvoire") if ($nation == 119);
		::rptMsg("The Country Is: Iraq") if ($nation == 121);
		::rptMsg("The Country Is: Japan") if ($nation == 122);
		::rptMsg("The Country Is: Jamaica") if ($nation == 124);
		::rptMsg("The Country Is: Jan Mayen") if ($nation == 125);
		::rptMsg("The Country Is: Jordan") if ($nation == 126);
		::rptMsg("The Country Is: Johnston Atoll") if ($nation == 127);
		::rptMsg("The Country Is: Kenya") if ($nation == 129);
		::rptMsg("The Country Is: Kyrgyzstan") if ($nation == 130);
		::rptMsg("The Country Is: North Korea") if ($nation == 131);
		::rptMsg("The Country Is: Kiribati") if ($nation == 133);
		::rptMsg("The Country Is: Korea") if ($nation == 134);
		::rptMsg("The Country Is: Kuwait") if ($nation == 136);
		::rptMsg("The Country Is: Kazakhstan") if ($nation == 137);
		::rptMsg("The Country Is: Laos") if ($nation == 138);
		::rptMsg("The Country Is: Lebanon") if ($nation == 139);
		::rptMsg("The Country Is: Latvia") if ($nation == 140);
		::rptMsg("The Country Is: Lithuania") if ($nation == 141);
		::rptMsg("The Country Is: Liberia") if ($nation == 142);
		::rptMsg("The Country Is: Slovakia") if ($nation == 143);
		::rptMsg("The Country Is: Liechtenstein") if ($nation == 145);
		::rptMsg("The Country Is: Lesotho") if ($nation == 146);
		::rptMsg("The Country Is: Luxembourg") if ($nation == 147);
		::rptMsg("The Country Is: Libya") if ($nation == 148);
		::rptMsg("The Country Is: Madagascar") if ($nation == 149);
		::rptMsg("The Country Is: Macao") if ($nation == 151);
		::rptMsg("The Country Is: Moldova") if ($nation == 152);
		::rptMsg("The Country Is: Mongolia") if ($nation == 154);
		::rptMsg("The Country Is: Malawi") if ($nation == 156);
		::rptMsg("The Country Is: Mali") if ($nation == 157);
		::rptMsg("The Country Is: Monaco") if ($nation == 158);
		::rptMsg("The Country Is: Morocco") if ($nation == 159);
		::rptMsg("The Country Is: Mauritius") if ($nation == 160);
		::rptMsg("The Country Is: Mauritania") if ($nation == 162);
		::rptMsg("The Country Is: Malta") if ($nation == 163);
		::rptMsg("The Country Is: Oman") if ($nation == 164);
		::rptMsg("The Country Is: Maldives") if ($nation == 165);
		::rptMsg("The Country Is: Mexico") if ($nation == 166);
		::rptMsg("The Country Is: Malaysia") if ($nation == 167);
		::rptMsg("The Country Is: Mozambique") if ($nation == 168);
		::rptMsg("The Country Is: Niger") if ($nation == 173);
		::rptMsg("The Country Is: Vanuatu") if ($nation == 174);
		::rptMsg("The Country Is: Nigeria") if ($nation == 175);
		::rptMsg("The Country Is: Netherlands") if ($nation == 176);
		::rptMsg("The Country Is: Norway") if ($nation == 177);
		::rptMsg("The Country Is: Nepal") if ($nation == 178);
		::rptMsg("The Country Is: Nauru") if ($nation == 180);
		::rptMsg("The Country Is: Suriname") if ($nation == 181);
		::rptMsg("The Country Is: Nicaragua") if ($nation == 182);
		::rptMsg("The Country Is: New Zealand") if ($nation == 183);
		::rptMsg("The Country Is: Palestinian Authority") if ($nation == 184);
		::rptMsg("The Country Is: Paraguay") if ($nation == 185);
		::rptMsg("The Country Is: Peru") if ($nation == 187);
		::rptMsg("The Country Is: Pakistan") if ($nation == 190);
		::rptMsg("The Country Is: Poland") if ($nation == 191);
		::rptMsg("The Country Is: Panama") if ($nation == 192);
		::rptMsg("The Country Is: Portugal") if ($nation == 193);
		::rptMsg("The Country Is: Papua New Guinea") if ($nation == 194);
		::rptMsg("The Country Is: Palau") if ($nation == 195);
		::rptMsg("The Country Is: Guinea-Bissau") if ($nation == 196);
		::rptMsg("The Country Is: Qatar") if ($nation == 197);
		::rptMsg("The Country Is: Réunion") if ($nation == 198);
		::rptMsg("The Country Is: Marshall Islands") if ($nation == 199);
		::rptMsg("The Country Is: Romania") if ($nation == 200);
		::rptMsg("The Country Is: Philippines") if ($nation == 201);
		::rptMsg("The Country Is: Puerto Rico") if ($nation == 202);
		::rptMsg("The Country Is: Russia") if ($nation == 203);
		::rptMsg("The Country Is: Rwanda") if ($nation == 204);
		::rptMsg("The Country Is: Saudi Arabia") if ($nation == 205);
		::rptMsg("The Country Is: Saint Pierre and Miquelon") if ($nation == 206);
		::rptMsg("The Country Is: Saint Kitts and Nevis") if ($nation == 207);
		::rptMsg("The Country Is: Seychelles") if ($nation == 208);
		::rptMsg("The Country Is: South Africa") if ($nation == 209);
		::rptMsg("The Country Is: Senegal") if ($nation == 210);
		::rptMsg("The Country Is: Slovenia") if ($nation == 212);
		::rptMsg("The Country Is: Sierra Leone") if ($nation == 213);
		::rptMsg("The Country Is: San Marino") if ($nation == 214);
		::rptMsg("The Country Is: Singapore") if ($nation == 215);
		::rptMsg("The Country Is: Somalia") if ($nation == 216);
		::rptMsg("The Country Is: Spain") if ($nation == 217);
		::rptMsg("The Country Is: Saint Lucia") if ($nation == 218);
		::rptMsg("The Country Is: Sudan") if ($nation == 219);
		::rptMsg("The Country Is: Svalbard") if ($nation == 220);
		::rptMsg("The Country Is: Sweden") if ($nation == 221);
		::rptMsg("The Country Is: Syria") if ($nation == 222);
		::rptMsg("The Country Is: Switzerland") if ($nation == 223);
		::rptMsg("The Country Is: United Arab Emirates") if ($nation == 224);
		::rptMsg("The Country Is: Trinidad and Tobago") if ($nation == 225);
		::rptMsg("The Country Is: Thailand") if ($nation == 227);
		::rptMsg("The Country Is: Tajikistan") if ($nation == 228);
		::rptMsg("The Country Is: Tonga") if ($nation == 231);
		::rptMsg("The Country Is: Togo") if ($nation == 232);
		::rptMsg("The Country Is: São Tomé and Príncipe") if ($nation == 233);
		::rptMsg("The Country Is: Tunisia") if ($nation == 234);
		::rptMsg("The Country Is: Turkey") if ($nation == 235);
		::rptMsg("The Country Is: Tuvalu") if ($nation == 236);
		::rptMsg("The Country Is: Taiwan") if ($nation == 237);
		::rptMsg("The Country Is: Turkmenistan") if ($nation == 238);
		::rptMsg("The Country Is: Tanzania") if ($nation == 239);
		::rptMsg("The Country Is: Uganda") if ($nation == 240);
		::rptMsg("The Country Is: Ukraine") if ($nation == 241);
		::rptMsg("The Country Is: United Kingdom") if ($nation == 242);
		::rptMsg("The Country Is: United States") if ($nation == 244);
		::rptMsg("The Country Is: Burkina Faso") if ($nation == 245);
		::rptMsg("The Country Is: Uruguay") if ($nation == 246);
		::rptMsg("The Country Is: Uzbekistan") if ($nation == 247);
		::rptMsg("The Country Is: Saint Vincent and the Grenadines") if ($nation == 248);
		::rptMsg("The Country Is: Venezuela") if ($nation == 249);
		::rptMsg("The Country Is: Vietnam") if ($nation == 251);
		::rptMsg("The Country Is: U.S. Virgin Islands") if ($nation == 252);
		::rptMsg("The Country Is: Vatican City") if ($nation == 253);
		::rptMsg("The Country Is: Namibia") if ($nation == 254);
		::rptMsg("The Country Is: Wake Island") if ($nation == 258);
		::rptMsg("The Country Is: Samoa") if ($nation == 259);
		::rptMsg("The Country Is: Swaziland") if ($nation == 260);
		::rptMsg("The Country Is: Yemen") if ($nation == 261);
		::rptMsg("The Country Is: Zambia") if ($nation == 263);
		::rptMsg("The Country Is: Zimbabwe") if ($nation == 264);
		::rptMsg("The Country Is: Serbia and Montenegro (Former)") if ($nation == 269);
		::rptMsg("The Country Is: Montenegro") if ($nation == 270);
		::rptMsg("The Country Is: Serbia") if ($nation == 271);
		::rptMsg("The Country Is: Curaçao") if ($nation == 273);
		::rptMsg("The Country Is: Anguilla") if ($nation == 300);
		::rptMsg("The Country Is: South Sudan") if ($nation == 276);
		::rptMsg("The Country Is: Antarctica") if ($nation == 301);
		::rptMsg("The Country Is: Aruba") if ($nation == 302);
		::rptMsg("The Country Is: Ascension Island") if ($nation == 303);
		::rptMsg("The Country Is: Ashmore and Cartier Islands") if ($nation == 304);
		::rptMsg("The Country Is: Baker sland") if ($nation == 305);
		::rptMsg("The Country Is: Bouvet Island") if ($nation == 306);
		::rptMsg("The Country Is: Cayman Islands") if ($nation == 307);
		::rptMsg("The Country Is: Channel Islands") if ($nation == 308);
		::rptMsg("The Country Is: Christmas Island") if ($nation == 309);
		::rptMsg("The Country Is: Clipperton Island") if ($nation == 310);
		::rptMsg("The Country Is: Cocos (Keeling) Islands") if ($nation == 311);
		::rptMsg("The Country Is: Cook Islands") if ($nation == 312);
		::rptMsg("The Country Is: Coral Sea Islands") if ($nation == 313);
		::rptMsg("The Country Is: Diego Garcia") if ($nation == 314);
		::rptMsg("The Country Is: Falkland Islands") if ($nation == 315);
		::rptMsg("The Country Is: French Guiana") if ($nation == 317);
		::rptMsg("The Country Is: French Polynesia") if ($nation == 318);
		::rptMsg("The Country Is: French Southern Territories") if ($nation == 319);
		::rptMsg("The Country Is: Guadeloupe") if ($nation == 321);
		::rptMsg("The Country Is: Guam") if ($nation == 322);
		::rptMsg("The Country Is: Guantanamo Bay") if ($nation == 323);
		::rptMsg("The Country Is: Guernsey") if ($nation == 324);
		::rptMsg("The Country Is: Heard Island and Mcdonald Islands") if ($nation == 325);
		::rptMsg("The Country Is: Howland Island") if ($nation == 326);
		::rptMsg("The Country Is: Jarvis Island") if ($nation == 327);
		::rptMsg("The Country Is: Jersey") if ($nation == 328);
		::rptMsg("The Country Is: Kingman Reef") if ($nation == 329);
		::rptMsg("The Country Is: Martinique") if ($nation == 330);
		::rptMsg("The Country Is: Mayotte") if ($nation == 331);
		::rptMsg("The Country Is: Montserrat") if ($nation == 332);
		::rptMsg("The Country Is: Netherlands Antilles (Former)") if ($nation == 333);
		::rptMsg("The Country Is: New Caledonia") if ($nation == 334);
		::rptMsg("The Country Is: Niue") if ($nation == 335);
		::rptMsg("The Country Is: Norfolk Island") if ($nation == 336);
		::rptMsg("The Country Is: Northern Mariana Islands") if ($nation == 337);
		::rptMsg("The Country Is: Palmyra Atoll") if ($nation == 338);
		::rptMsg("The Country Is: Pitcairn Islands") if ($nation == 339);
		::rptMsg("The Country Is: Rota Island") if ($nation == 340);
		::rptMsg("The Country Is: Saipan") if ($nation == 341);
		::rptMsg("The Country Is: South Georgia and the South Sandwich Islands") if ($nation == 342);
		::rptMsg("The Country Is: St Helena Ascension and Tristan da Cunha") if ($nation == 343);
		::rptMsg("The Country Is: Tinian Island") if ($nation == 346);
		::rptMsg("The Country Is: Tokelau") if ($nation == 347);
		::rptMsg("The Country Is: Tristan da Cunha") if ($nation == 348);
		::rptMsg("The Country Is: Turks and Caicos Islands") if ($nation == 349);
		::rptMsg("The Country Is: British Virgin Islands") if ($nation == 351);
		::rptMsg("The Country Is: Wallis and Futuna") if ($nation == 352);
		::rptMsg("The Country Is: Africa") if ($nation == 742);
		::rptMsg("The Country Is: Asia") if ($nation == 2129);
		::rptMsg("The Country Is: Europe") if ($nation == 10541);
		::rptMsg("The Country Is: Isle of Man") if ($nation == 15126);
		::rptMsg("The Country Is: Macedonia") if ($nation == 19618);
		::rptMsg("The Country Is: Melanesia") if ($nation == 20900);
		::rptMsg("The Country Is: Micronesia") if ($nation == 21206);
		::rptMsg("The Country Is: Midway Islands") if ($nation == 21242);
		::rptMsg("The Country Is: Northern America") if ($nation == 23581);
		::rptMsg("The Country Is: Polynesia") if ($nation == 26286);
		::rptMsg("The Country Is: Central America") if ($nation == 27082);
		::rptMsg("The Country Is: Oceania") if ($nation == 27114);
		::rptMsg("The Country Is: Sint Maarten") if ($nation == 30967);
		::rptMsg("The Country Is: South America") if ($nation == 31396);
		::rptMsg("The Country Is: Saint Martin") if ($nation == 31706);
		::rptMsg("The Country Is: World") if ($nation == 39070);
		::rptMsg("The Country Is: Western Africa") if ($nation == 42483);
		::rptMsg("The Country Is: Middle Africa") if ($nation == 42484);
		::rptMsg("The Country Is: Northern Africa") if ($nation == 42487);
		::rptMsg("The Country Is: Central Asia") if ($nation == 47590);
		::rptMsg("The Country Is: South-Eastern Asia") if ($nation == 47599);
		::rptMsg("The Country Is: Eastern Asia") if ($nation == 47600);
		::rptMsg("The Country Is: Eastern Africa") if ($nation == 47603);
		::rptMsg("The Country Is: Eastern Europe") if ($nation == 47609);
		::rptMsg("The Country Is: Southern Europe") if ($nation == 47610);
		::rptMsg("The Country Is: Middle East") if ($nation == 47611);
		::rptMsg("The Country Is: Southern Asia") if ($nation == 47614);
		::rptMsg("The Country Is: Timor-Leste") if ($nation == 7299303);
		::rptMsg("The Country Is: Kosovo") if ($nation == 9914689);
		::rptMsg("The Country Is: Americas") if ($nation == 10026358);
		::rptMsg("The Country Is: Åland Islands") if ($nation == 10028789);
		::rptMsg("The Country Is: Caribbean") if ($nation == 10039880);
		::rptMsg("The Country Is: Northern Europe") if ($nation == 10039882);
		::rptMsg("The Country Is: Southern Africa") if ($nation == 10039883);
		::rptMsg("The Country Is: Western Europe") if ($nation == 10210824);
		::rptMsg("The Country Is: Australia and New Zealand") if ($nation == 10210825);
		::rptMsg("The Country Is: Saint Barthélemy") if ($nation == 161832015);
		::rptMsg("The Country Is: U.S. Minor Outlying Islands") if ($nation == 161832256);
		::rptMsg("The Country Is: Latin America and the Caribbean") if ($nation == 161832257);
		::rptMsg("The Country Is: Bonaire Saint Eustatius and Saba") if ($nation == 161832258);
		::rptMsg("For more information please visit the link below:");
		::rptMsg("https://msdn.microsoft.com/en-us/library/aa723531.aspx");



	}

	else {
		::rptMsg($key_path." not found.");
	}


	::rptMsg("");

}
1;
