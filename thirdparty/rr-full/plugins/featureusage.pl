#-----------------------------------------------------------
# featureusage.pl
#   
#
# Change history
#   20200911 - MITRE updates
#   20200511 - update date output format
#   20190919 - created
#
#   Note: at this point, the context of the data is not really understood...
#
# References
#   https://www.crowdstrike.com/blog/how-to-employ-featureusage-for-windows-10-taskbar-forensics/
#
#	https://attack.mitre.org/techniques/T1059/
#
# Copyright 2020 QAR, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package featureusage;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1059",
              category      => "program execution",
              version       => 20200911);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts user's FeatureUsage data";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching featureusage v.".$VERSION);
	::rptMsg("featureusage v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FeatureUsage";
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time:  ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my ($t0,$t1) = unpack("VV",$key->get_value("KeyCreationTime")->get_data());
			::rptMsg("KeyCreationTime: ".::format8601Date(::getTime($t0,$t1))."Z");
			::rptMsg("");
		};
		
		eval {
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar @subkeys > 0) {
				foreach my $s (@subkeys) {
					my $subkey_name = $s->get_name();
					if (my $app = $key->get_subkey($subkey_name)) {
						my @vals = $app->get_list_of_values();
						if (scalar @vals > 0) {
							::rptMsg("***".$subkey_name." values***");
							foreach my $val (@vals) {
								my $name = $val->get_name();
								my $data = $val->get_data();
								::rptMsg(sprintf "%-80s  ".$data,$name);
							}
						}
					}
					::rptMsg("");
				}
			}
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;
