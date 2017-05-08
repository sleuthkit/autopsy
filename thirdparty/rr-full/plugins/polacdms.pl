#-----------------------------------------------------------
# polacdms
# Get the audit policy from the Security hive file; also, gets
# 
# 
# Change History:
#   20100531 - Created
#
# References:
#   http://en.wikipedia.org/wiki/Security_Identifier
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package polacdms;
use strict;

my %config = (hive          => "Security",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100531);

sub getConfig{return %config}
sub getShortDescr {
	return "Get local machine SID from Security hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching polacdms v.".$VERSION);
	::rptMsg("polacdms v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Policy\\PolAcDmS";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("PolAcDmS");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $data;
		eval {
			$data = $key->get_value("")->get_data();
		};
		if ($@) {
			::rptMsg("Error occurred getting data from ".$key_path);
			::rptMsg(" - ".$@);
		}
		else {
			my @d = unpack("V4",substr($data,8,16));
			::rptMsg("Machine SID: S-1-5-".(join('-',@d)));
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	$key_path = "Policy\\PolPrDmS";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("PolPrDmS");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my $data;
		eval {
			$data = $key->get_value("")->get_data();
		};
		if ($@) {
			::rptMsg("Error occurred getting data from ".$key_path);
			::rptMsg(" - ".$@);
		}
		else {
			my @d = unpack("V4",substr($data,8,16));
			::rptMsg("Primary Domain SID: S-1-5-".(join('-',@d)));
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
