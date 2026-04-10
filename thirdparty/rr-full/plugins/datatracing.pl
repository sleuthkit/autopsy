#-----------------------------------------------------------
# datatracing.pl
#   
#
# Change history
#   20201018 - created
#
# References
#   https://docs.microsoft.com/en-us/previous-versions/sql/sql-server-2008/cc765421(v=sql.100)
#   https://www.hexacorn.com/blog/2020/10/17/beyond-good-ol-run-key-part-129/
#
#  https://attack.mitre.org/techniques/T1546/
#
# Copyright 2020 Quantum Analytics Research, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package datatracing;
use strict;

my %config = (hive          => "software",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 0,
			  output		=> "report",
              MITRE         => "T1546",
              category      => "persistence",
              version       => 20201018);
my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {
	return "Checks for MS SQL data tracing DLL";
}
sub getShortDescr {
	return "Checks for MS SQL data tracing DLL";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching datatracing v.".$VERSION);
	::rptMsg("datatracing v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");    
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("Microsoft\\BidInterface\\Loader",
	             "Wow6432Node\\Microsoft\\BidInterface\\Loader");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
			::rptMsg("");

			my $bid = ();
			eval {
				$bid = $key->get_value(":Path")->get_data();
				::rptMsg(":Path value = ".$bid);
				::rptMsg("");
			};
			::rptMsg("Analysis Tip: A data tracing DLL can be added to MS SQL, providing persistence via the \":Path\" value.");
			::rptMsg("");
			::rptMsg("Ref: https://docs.microsoft.com/en-us/previous-versions/sql/sql-server-2008/cc765421(v=sql.100)");
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	} 
	::rptMsg("");
}

1;
