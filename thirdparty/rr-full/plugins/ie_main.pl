#-----------------------------------------------------------
# ie_main.pl
# Checks keys/values set by new version of Trojan.Clampi
#
# Change history
#   20091019 - created
#
#
# References
#   http://support.microsoft.com/kb/895339
#   http://support.microsoft.com/kb/176497
# 
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package ie_main;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20091019);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets values beneath user's Internet Explorer\\Main key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ie_main v.".$VERSION);
	::rptMsg("ie_main v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Internet Explorer\\Main';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		
		my %main;
		
		my @vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $data = $v->get_data();
				next if ($name eq "Window_Placement");
				
				$data = unpack("V",$data) if ($name eq "Do404Search");
				
				if ($name eq "IE8RunOnceLastShown_TIMESTAMP" || $name eq "IE8TourShownTime") {
					my ($t0,$t1) = unpack("VV",$data);
					$data = gmtime(::getTime($t0,$t1))." UTC";
				}
				$main{$name} = $data;
			}
		
			foreach my $n (keys %main) {
				my $str = sprintf "%-35s  %-20s",$n,$main{$n};
				::rptMsg($str);
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