#-----------------------------------------------------------
# vista_comdlg32.pl
# Plugin for Registry Ripper 
#
# Change history
#   20090821 - created
#
# References
#  
#   
#		
# copyright 2009 H. Carvey
#-----------------------------------------------------------
package vista_comdlg32;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20090821);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of Vista user's ComDlg32 key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching vista_comdlg32 v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("vista_comdlg32 v.".$VERSION);
	::rptMsg("**All values listed in MRU order.");
	
# CIDSizeMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComDlg32\\CIDSizeMRU";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my %lvmru;
		my @mrulist;
		@vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
# First, read in all of the values and the data
			foreach my $v (@vals) {
				$lvmru{$v->get_name()} = $v->get_data();
			}
# Then, remove the MRUList value
			if (exists $lvmru{MRUListEx}) {
				delete($lvmru{MRUListEx});
				foreach my $m (keys %lvmru) {
					my $file = parseStr($lvmru{$m});
					my $str = sprintf "%-4s ".$file,$m;
					::rptMsg("  ".$str);
				}
			}
			else {
				::rptMsg($key_path." does not have an MRUList value.");
			}				
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	
# LastVistedPidlMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComDlg32\\LastVisitedPidlMRU";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		my %lvmru;
		my @mrulist;
		@vals = $key->get_list_of_values();
		
		if (scalar(@vals) > 0) {
# First, read in all of the values and the data
			foreach my $v (@vals) {
				$lvmru{$v->get_name()} = $v->get_data();
			}
# Then, remove the MRUList value
			if (exists $lvmru{MRUListEx}) {
				delete($lvmru{MRUListEx});
				foreach my $m (keys %lvmru) {
					my $file = parseStr($lvmru{$m});
					my $str = sprintf "%-4s ".$file,$m;
					::rptMsg("  ".$str);
				}
			}
			else {
				::rptMsg($key_path." does not have an MRUList value.");
			}				
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	

}	

sub parseStr {
	my $data = $_[0];
	my $temp;
	my $tag = 1;
	my $ofs = 0;
	
	while ($tag) {
		my $t = substr($data,$ofs,2);
		if (unpack("v",$t) == 0x00) {
			$tag = 0;
		}
		else {
			$temp .= $t;
			$ofs += 2;
		}
	}
	$temp =~ s/\00//g;
	return $temp;
}
1;		