#-----------------------------------------------------------
# userextendedproperties.pl
#  
# Change history
#  20220509 - created
#
# References
#  
# 
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package userextendedproperties;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "identity", 
              MITRE         => "",
			  output		=> "report",
              version       => 20220509);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets MS Live ID and account name mapping";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching userextendedproperties v.".$VERSION);
	::rptMsg("userextendedproperties v.".$VERSION); 
	::rptMsg("- ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\IdentityCRL\\UserExtendedProperties';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("Name        : ".$s->get_name());
				::rptMsg("LastWrite   : ".::format8601Date($s->get_timestamp())."Z");
				eval {
					my $cid = $s->get_value("cid")->get_data();
					::rptMsg("Microsoft ID: ".$cid);
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;