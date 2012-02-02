#-----------------------------------------------------------
# comdlg32.pl
# Plugin for Registry Ripper 
#
# Change history
#   20100402 - updated IAW Chad Tilbury's post to SANS
#              Forensic Blog
#   20080324 - created
#
# References
#   Win2000 - http://support.microsoft.com/kb/319958
#   XP - http://support.microsoft.com/kb/322948/EN-US/
#		
# copyright 20100402 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package comdlg32;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100402);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's ComDlg32 key";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching comdlg32 v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	::rptMsg("comdlg32 v.".$VERSION);
	
# LastVistedMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComDlg32\\LastVisitedMRU";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ComDlg32\\LastVisitedMRU");
		::rptMsg("**All values printed in MRUList order.");
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
			if (exists $lvmru{MRUList}) {
				::rptMsg("  MRUList = ".$lvmru{MRUList});
				@mrulist = split(//,$lvmru{MRUList});
				delete($lvmru{MRUList});
				foreach my $m (@mrulist) {
					my ($file,$dir) = split(/\00\00/,$lvmru{$m},2);
					$file =~ s/\00//g;
					$dir  =~ s/\00//g;
					::rptMsg("  ".$m." -> EXE: ".$file);
					::rptMsg("    -> Last Dir: ".$dir);
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
	
# OpenSaveMRU	
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\ComDlg32\\OpenSaveMRU";
	my $key;
	my @vals;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ComDlg32\\OpenSaveMRU");
		::rptMsg("**All values printed in MRUList order.");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
# First, process OpenSaveMRU key values
		parseOpenSaveValues($key);
		::rptMsg("");
# Now, let's get the subkeys
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				parseOpenSaveValues($s);
				::rptMsg("");
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}	

sub parseOpenSaveValues {
	my $key = shift;
	::rptMsg("OpenSaveMRU\\".$key->get_name());
	::rptMsg("LastWrite Time: ".gmtime($key->get_timestamp())." Z");
	my %osmru;
	my @vals = $key->get_list_of_values();
	if (scalar(@vals) > 0) {
		map{$osmru{$_->get_name()} = $_->get_data()}(@vals);
		if (exists $osmru{MRUList}) {
			::rptMsg("  MRUList = ".$osmru{MRUList});
			my @mrulist = split(//,$osmru{MRUList});
			delete($osmru{MRUList});
			foreach my $m (@mrulist) {
				::rptMsg("  ".$m." -> ".$osmru{$m});
			}
		}
		else {
			::rptMsg($key->get_name()." does not have an MRUList value.");
		}	
	}
	else {
		::rptMsg($key->get_name()." has no values.");
	}	
}


1;		