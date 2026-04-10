#-----------------------------------------------------------
# improviders.pl
#   Extracts IM Providers info from NTUSER.DAT 
# 
# Change history
#   20201015 - created
#
# References
#
# Copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package improviders;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              category      => "user activity",
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20201015);

my $VERSION = getVersion();

sub getDescr {}
sub getRefs {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Get IM providers from NTUSER\.DAT";
}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching improviders v.".$VERSION);
  ::rptMsg("improviders v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key = ();
	my $key_path = "Software\\IM Providers";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $app = $key->get_value("DefaultIMApp")->get_data();
			::rptMsg("DefaultIMApp       = ".$app);
			::rptMsg("");
		};
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				
				eval {
					my $up = $s->get_value("UpAndRunning")->get_data();
					::rptMsg("UpAndRunning value = ".$up);
				};
				
				eval {
					my $pid = $s->get_value("ProcessID")->get_data();
					::rptMsg("ProcessID value    = ".$pid);
				};
				
				
				::rptMsg("");
			}
		}
	}
}

1;
