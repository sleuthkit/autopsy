#-----------------------------------------------------------
# vncviewer
#
#
#-----------------------------------------------------------
package vncviewer;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080325);
              
sub getConfig{return %config}
sub getShortDescr {
	return "Get VNCViewer system list";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching vncviewer v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\ORL\\VNCviewer\\MRU";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("VNCViewer\\MRU");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %vnc;
			foreach my $v (@vals) {
				$vnc{$v->get_name()} = $v->get_data();
			}
			my $ind;
			if (exists $vnc{'index'}) {
				$ind = $vnc{'index'};
				delete $vnc{'index'};
			}
			
			::rptMsg("Index = ".$ind);
			my @i = split(//,$ind);
			foreach my $i (@i) {
				::rptMsg("  ".$i." -> ".$vnc{$i});
			}
		}
		else {
			::rptMsg($key_path." has no values.");
			::logMsg($key_path." has no values.");	
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;