#-----------------------------------------------------------
# installproperties
#
# Change history:
#  20221031 - created
# 
# Ref:
#  https://twitter.com/SBousseaden/status/1586862562624299010
#  https://twitter.com/Arkbird_SOLG/status/1131178793350193153
#
# copyright 2022 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package installproperties;
use strict;

my %config = (hive          => "software",
			  category      => "execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              MITRE         => "T1204\.002",
              version       => 20221031);

sub getConfig{return %config}
sub getShortDescr {
	return "Get InstallProperties settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching installproperties v.".$VERSION);
	::rptMsg("installproperties v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $key_path = ('Microsoft\\Windows\\CurrentVersion\\Installer\\UserData');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys1 = $key->get_list_of_subkeys();
		if (scalar @subkeys1 > 0) {
			foreach my $sk1 (@subkeys1) {
				my $key_path2 = $key_path."\\".$sk1->get_name()."\\Products";
				if (my $key2 = $root_key->get_subkey($key_path2)) {
					my @subkeys2 = $key2->get_list_of_subkeys();
					if (scalar @subkeys2 > 0) {
						foreach my $sk2 (@subkeys2) {
							
							eval {
								my $d = $sk2->get_subkey("InstallProperties")->get_value("DisplayName")->get_data();
								::rptMsg("DisplayName: ".$d);
							};
							
							eval {
								my $d = $sk2->get_subkey("InstallProperties")->get_value("InstallDate")->get_data();
								::rptMsg("  InstallDate: ".$d);
#								::rptMsg("  Key LastWrite Time ".::format8601Date($sk2->get_timestamp())."Z");
							};
							
							eval {
								my $d = $sk2->get_subkey("InstallProperties")->get_value("InstallSource")->get_data();
								::rptMsg("  InstallSource: ".$d);
							};
						
							::rptMsg("");
						}
					}
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;