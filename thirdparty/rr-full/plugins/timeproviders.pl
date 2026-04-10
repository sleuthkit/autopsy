#-----------------------------------------------------------
# timeproviders.pl
#
# History:
#  20230125 - created
#
# References:
#  https://github.com/blackc03r/OSCP-Cheatsheets/blob/master/offensive-security/persistence/t1209-hijacking-time-providers.md
#  https://attack.mitre.org/techniques/T1547/003/
# 
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package timeproviders;
use strict;

my %config = (hive          => "System",
			  output        => "report",
			  category      => "program execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547\.003",  
              version       => 20200813);

sub getConfig{return %config}
sub getShortDescr {
	return "Check time providers for hijacking";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching timeproviders v.".$VERSION);
	::rptMsg("timeproviders v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("Category: ".$config{category}." - ".$config{MITRE});
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $ccs = ::getCCS($root_key);
	my @providers = ("NtpClient", "NtpServer");
	my $key;
	foreach my $p (@providers) {
		my $key_path = $ccs."\\Services\\W32Time\\TimeProviders\\".$p;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("");
			::rptMsg($key_path);
			::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			
			eval {
				my $n = $key->get_value("DllName")->get_data();
				::rptMsg("DllName value: ".$n);
			};

		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Threat actors can register a malicious time provider by changing the \"DllName\" value. The value should");
	::rptMsg("point to %systemroot%\\system32\\w32time\.dll\.");
	::rptMsg("");
	::rptMsg("Ref: https://github.com/blackc03r/OSCP-Cheatsheets/blob/master/offensive-security/persistence/t1209-hijacking-time-providers.md");
	::rptMsg("Ref: https://attack.mitre.org/techniques/T1547/003/");
}

1;