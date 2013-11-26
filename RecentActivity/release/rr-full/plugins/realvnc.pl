#-----------------------------------------------------------
# realvnc.pl
# Plugin to get RealVNC MRU listings from NTUSER.DAT 
#
# Change history
#   20091125 - created
#
# References
#
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package realvnc;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20091125);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets user's RealVNC MRU listing";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching realvnc v.".$VERSION);
	::rptMsg("realvnc v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\RealVNC\\VNCViewer4\\MRU';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %mru;
			my @order;
			foreach my $v (@vals) { 
				$mru{$v->get_name()} = $v->get_data();
			}
				
			if (exists($mru{Order})) {
				@order = unpack("C*",$mru{Order});				
# List systems connected to based on Order MRU value	
				::rptMsg("*Systems output in \"Order\" sequence");		
				foreach my $i (0..scalar(@order) - 1) {
					$order[$i] = "0".$order[$i] if ($order[$i] < 10);
					::rptMsg("  ".$order[$i]." -> ".$mru{$order[$i]});
				}
			}
			else {
				::rptMsg("Could not find Order value.");
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