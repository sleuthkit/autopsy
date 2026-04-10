#-----------------------------------------------------------
# volsnap.pl
#  Values beneath VSS\Diag subkeys (including VolSnap) have timestamps embedded in
#  the data; wrote the plugin to extract the info, to be used in research to determine
#  if there's value to the data
#
# History:
#  20210128 - created
#
# References:
#  https://twitter.com/0gtweet/status/1354766164166115331
#  
# 
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package volsnap;
use strict;

my %config = (hive          => "System",
			  category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20210128);

sub getConfig{return %config}
sub getShortDescr {
	return "Check VSS\\Diag settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my $str = "";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching volsnap v.".$VERSION);
	::rptMsg("volsnap v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $ccs = ::getCCS($root_key);
	my @subkeys = ("VolSnap","SPP","SystemRestore");
	my $key_path = $ccs."\\Services\\VSS\\Diag";
	my $key = ();
	if ($key = $root_key->get_subkey($key_path)) {
		foreach my $s (@subkeys) {
			if (my $k = $key->get_subkey($s)) {
				my @vals = $k->get_list_of_values();
				if (scalar @vals > 0) {
					foreach my $v (@vals) {
						
						my $name = $v->get_name();
						my $data = $v->get_data();
						my ($t0,$t1) = unpack("VV",substr($data,8,8));
						my $ts   = ::format8601Date(::getTime($t0,$t1));
						
						::rptMsg($ts."Z  ".$s."\\".$name);
						
					}
				}
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}	
	::rptMsg("");
	::rptMsg("Analysis Tip: No tip; as of 20210128, this plugin is for testing purposes.");
#	::rptMsg("");
}

1;