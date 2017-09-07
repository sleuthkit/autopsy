#-----------------------------------------------------------
# licenses.pl
#   There are indications that the contents of this key may be associated
#   with a number of different malware variants, including the Elite 
#   Keylogger.
#
# History
#   20120305 - created
#   
#
# copyright 2012, Quantum Analytics Research, LLC
#-----------------------------------------------------------
package licenses;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20120305);

sub getConfig{return %config}

sub getShortDescr {
	return "Get contents of HKLM/Software/Licenses key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
	::logMsg("Launching licenses v.".$VERSION);
	::rptMsg("licenses v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Licenses";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				if ($v->get_type() == 3) {
					::rptMsg("Value: ".$v->get_name()." (Binary data: ".length($v->get_data())." bytes)");
				}
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;