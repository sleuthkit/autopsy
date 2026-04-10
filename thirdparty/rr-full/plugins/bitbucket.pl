#-----------------------------------------------------------
# bitbucket.pl
#  
# Change history
#  20221129 - created
#
# References
#  
# 
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package bitbucket;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              category      => "defense evasion", 
              MITRE         => "T1562\.001",
              version       => 20221129);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's BitBucket settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching bitbucket v.".$VERSION);
	::rptMsg("bitbucket v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());  
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\BitBucket\\Volume';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar(@subkeys) > 0) {
			foreach my $s (@subkeys) {
				::rptMsg("Volume GUID: ".$s->get_name());
				::rptMsg("LastWrite time: ".::format8601Date($s->get_timestamp())."Z");
				
				eval {
					my $c = $s->get_value("MaxCapacity")->get_data();
					::rptMsg(sprintf "%-15s %-8s MB","MaxCapacity",$c);
				};
				
				eval {
					my $n = $s->get_value("NukeOnDelete")->get_data();
					::rptMsg(sprintf "%-15s 0x%04x","NukeOnDelete",$n);
				};
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
		::rptMsg("Analysis Tip: Volume GUIDs can be mapped to MountedDevices key to determine drive letter(s).");
		::rptMsg("MaxCapacity is max capacity of the Recycle Bin for the volume, in MB.");
		::rptMsg("NukeOnDelete corresponds to \"Don't move files to the Recycle Bin\. Remove files immediately when deleted.\"");
		::rptMsg("  0 - disabled");
		::rptMsg("  1 - enabled");
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;