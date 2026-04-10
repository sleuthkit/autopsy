#-----------------------------------------------------------
# mpnotify.pl
# Get WinLogon mpnotify setting
#
# Change history:
#   20230702 - added reference
#   20211025 - created
#
# References:
#   https://twitter.com/0gtweet/status/1372550832416260103
#   https://persistence-info.github.io/Data/mpnotify.html
#   
#        
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package mpnotify;
use strict;

my %config = (hive          => "software",
			  category      => "persistence",
			  MITRE         => "T1546",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20230702);

sub getConfig{return %config}

sub getShortDescr {
	return "Get WinLogon mpnotify setting";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching mpnotify v.".$VERSION);
	::rptMsg("mpnotify v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @paths = ("Microsoft\\Windows NT\\CurrentVersion\\WinLogon");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg("Key path: ".$key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			eval {
				my $a = $key->get_value("mpnotify")->get_data();
				::rptMsg("mpnotify value     : ".$a);
			};
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: The mpnotify value registers an RPC endpoint, WinLogon binds to it and passes the password. The EXE will");
	::rptMsg("launch and exit after approx. 30 sec.");
	::rptMsg("");
	::rptMsg("Ref: https://persistence-info.github.io/Data/mpnotify.html");
}
1;