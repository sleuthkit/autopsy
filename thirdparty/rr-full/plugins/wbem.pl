#-----------------------------------------------------------
# wbem.pl
#   There are indications that the contents of this key may be associated
#   with a number of different malware variants, including the Elite 
#   Keylogger.
#
# History
#   20200916 - MITRE updates
#   20200511 - updated date output format
#   20190729 - Updated with 'autorecover mofs' info
#   20120306 - created
# 
# Ref:
#   https://twitter.com/king5in/status/1022024264910815232  
#
# copyright 2020, Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package wbem;
use strict;

my %config = (hive          => "Software",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200916);

sub getConfig{return %config}

sub getShortDescr {
	return "Get some contents from WBEM key";	
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
	::logMsg("Launching wbem v.".$VERSION);
	::rptMsg("wbem v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\WBEM\\WDM";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg($v->get_name()." - ".$v->get_data());
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}

# Added 20190729
# Ref: https://docs.microsoft.com/en-us/windows/win32/wmisdk/pragma-autorecover	
# Ref: https://twitter.com/mattifestation/status/1021879005815816192
	$key_path = "Microsoft\\WBEM\\CIMOM";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		
		my $mofs;
		my $moftime;
		eval {
			$moftime = $key->get_value("Autorecover MOFs Timestamp")->get_data();
			::rptMsg("");
		};
		
		eval {
			$mofs = $key->get_value("Autorecover MOFs")->get_data();
			::rptMsg("Autorecover MOFs: ".$mofs);
		};
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;