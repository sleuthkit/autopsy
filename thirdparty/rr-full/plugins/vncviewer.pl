#-----------------------------------------------------------
# vncviewer
#
# 
# History:
#   20121231 - Updated to include VNCViewer4
#   20080325 - created
#
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
              version       => 20121231);
              
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
	::rptMsg("vncviewer v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
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
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." has no values.");	
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
	my $key_path = "Software\\RealVNC\\VNCViewer4\\MRU";
	my $key; 
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $type = $v->get_type();
				my $data;
				if ($type == 3) {
					$data = $v->get_data_as_string();
				}
				else {
					$data = $v->get_data();
				}
				
				::rptMsg(sprintf "%-8s  %-25s",$name,$data);
			}
			
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else { 
		::rptMsg($key_path." not found.");
	}
}
1;