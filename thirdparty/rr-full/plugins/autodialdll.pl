#-----------------------------------------------------------
# autodialdll.pl
# get autodialdll DLL
#
# History
#  20221026 - created
#
# References
#   https://www.mdsec.co.uk/2022/10/autodialdlling-your-way/
#   https://www.hexacorn.com/blog/2015/01/13/beyond-good-ol-run-key-part-24/
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package autodialdll;
use strict;
my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output        => "report",
              MITRE         => "T1546",
              category      => "persistence",
              version       => 20221026);

sub getConfig{return %config}
sub getShortDescr {
	return "Get AutodialDLL DLL";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my $key;
	
	::logMsg("Launching autodialdll v.".$VERSION);
	::rptMsg("autodialdll v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Services\\WinSock2\\Parameters";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
				
		eval {
			my $i = $key->get_value("AutodialDLL")->get_data();
			::rptMsg("AutodialDLL value: ".$i);
		};
		::rptMsg("AutodialDLL value not found.") if ($@);

		
		::rptMsg("");
		::rptMsg("Analysis Tip: The default setting for the AutodialDLL value is \"C:\\Windows\\system32\\rasadhlp\.dll\".");
		::rptMsg("Modifying the path to a different DLL has been observed being used for persistence, and it can also be used");
		::rptMsg("for lateral movement.");
		::rptMsg("");
		::rptMsg("Ref: https://www.mdsec.co.uk/2022/10/autodialdlling-your-way/");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1