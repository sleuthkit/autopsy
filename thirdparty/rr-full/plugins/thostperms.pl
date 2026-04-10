#-----------------------------------------------------------
# thostperms.pl
# Plugin for Registry Ripper 
# Parse Adobe Reader MRU keys
#
# Change history
#   20201015 - created
#
# References
#   
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package thostperms;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "user activity",
              MITRE         => "",
			  output		=> "report",
              version       => 20201015);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's THostPerms value from Acrobat Reader TrustManager";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching thostperms v.".$VERSION);
	::rptMsg("thostperms v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
# First, determine app version
	my $version;
	my $path = "Software\\Adobe\\Acrobat Reader";
	if (my $key = $root_key->get_subkey($path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				my $name = $s->get_name();
				if (defined($root_key->get_subkey($path."\\".$name."\\TrustManager"))) {
					$version = $name;
				}
			}
		}
	}
 
	my $key_path = "Software\\Adobe\\Acrobat Reader\\".$version."\\TrustManager\\cDefaultLaunchURLPerms";   
	my $key = "";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");

		my $thost = ();
		eval {
			$thost = $key->get_value("tHostPerms")->get_data();
#			::rptMsg("tHostPerms value = ".$thost);
			::rptMsg("tHostPerms values");
			my @vals = split(/\|/,$thost);
			foreach my $i (0..(scalar(@vals) - 1)) {
				if (substr($vals[$i],0,4) eq "file") {
					$vals[$i] = $vals[$i].":".$vals[$i + 1];
					splice @vals,$i + 1, 1;
				}
			}
			
			foreach my $v (@vals) {
				::rptMsg("  ".$v);
			}
			
		};
	}
}

1;