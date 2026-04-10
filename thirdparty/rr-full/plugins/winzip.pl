#-----------------------------------------------------------
# WinZip
# 
# History
#  20200803 - updates
#  20200526 - updated date output format
#  20140730 - updated to include mru/archives info
#  20080325 - created
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package winzip;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              category      => "user activity",
              MITRE         => "T1074",
			  output		=> "report",
              version       => 20200803);

sub getConfig{return %config}
sub getShortDescr {
	return "Get WinZip extract and filemenu values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching WinZip v.".$VERSION);
	::rptMsg("winzip v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Nico Mak Computing\\WinZip";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("WinZip");
		::rptMsg($key_path);
		::rptMsg("");
		my @subkeys = $key->get_list_of_subkeys();
		my %sk;
		foreach my $s (@subkeys) {
			$sk{$s->get_name()} = $s;
		}
		
		if (exists $sk{'extract'}) {
			my $tag = "extract";
			::rptMsg($key_path."\\extract  [".::format8601Date($sk{'extract'}->get_timestamp)."Z]");
			my @vals = $sk{'extract'}->get_list_of_values();
			my %ext;
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $num = $name;
				$num =~ s/^$tag//;
				$ext{$num} = $v->get_data();
			}
			foreach my $e (sort {$a <=> $b} keys %ext) {
				::rptMsg("  extract".$e." -> ".$ext{$e});
			}
			::rptMsg("");
		}
		else {
			::rptMsg("extract key not found.");
		}
		
		if (exists $sk{'filemenu'}) {
			my $tag = "filemenu";
			::rptMsg($key_path."\\filemenu  [".::format8601Date($sk{'filemenu'}->get_timestamp)."Z]");
			my @vals = $sk{'filemenu'}->get_list_of_values();
			my %ext;
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $num = $name;
				$num =~ s/^$tag//;
				$ext{$num} = $v->get_data();
			}
			foreach my $e (sort {$a <=> $b} keys %ext) {
				::rptMsg("  filemenu".$e." -> ".$ext{$e});
			}
		}
		else {
			::rptMsg("filemenu key not found.");
		}
		::rptMsg("");
# added 20140730
		my $archives;
		if ($archives = $key->get_subkey("mru\\archives")) {
			::rptMsg("mru\\archives subkey  [".::format8601Date($archives->get_timestamp())."Z]");
			
			my @vals = $archives->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					next if ($name eq "MRUList");
					::rptMsg(sprintf "%-4s  %s",$name, $v->get_data());
				}
			}
			else {
				::rptMsg("No values found.");
			}
		}
		else {
			::rptMsg("mru\\archives key not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;