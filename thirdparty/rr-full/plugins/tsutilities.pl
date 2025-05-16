#-----------------------------------------------------------
# tsutilities.pl
#
# 
# References:
#  https://www.hexacorn.com/blog/2020/07/30/beyond-good-ol-run-key-part-125/
#  https://twitter.com/0gtweet/status/1213745922942930945
#
# Change history:
#  20200806 - created
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tsutilities;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1547",
              category      => "persistence",
			  output		=> "report",
              version       => 20200806);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks TermServ Utilities";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching tsutilities v.".$VERSION);
	::rptMsg("tsutilities v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path;
	my $key;

# System Hive
	my $ccs = ::getCCS($root_key);
	
	$key_path = $ccs."\\Control\\Terminal Server\\Utilities";
	if ($key = $root_key->get_subkey($key_path)){
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("Name     : ".$s->get_name());
				::rptMsg("LastWrite: ".::format8601Date($s->get_timestamp())."Z");
				
				my @vals = $s->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						my $name = $v->get_name();
						my $data = $v->get_data();
						$data =~ s/\n/ /g;
						::rptMsg(sprintf "  %-15s %-30s",$name,$data);
					}
				}
				::rptMsg("");
			}
			::rptMsg("Analysis Tips: Look for new values added to the various keys, or key LastWrite times that occur during the incident");
			::rptMsg("  timeframe.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;