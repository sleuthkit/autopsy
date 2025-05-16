#-----------------------------------------------------------
# applets.pl
# Plugin for Registry Ripper 
# Windows\CurrentVersion\Applets Recent File List values 
#
# Change history
#  20201020 - Added check for RegEdit Favorites
#  20200813 - MITRE updates
#  20200525 - updated date output format
#  20140723 - updated to address issues of keys/values not in existence
#  20080324 - created
#
# References
#
# 
# copyright 2020 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package applets;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              category      => "program execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1204",
			  output        => "report",
              version       => 20201020);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of user's Applets key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching applets v.".$VERSION);
	::rptMsg("applets v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Applets';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("Applets");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
# Locate files opened in MS Paint		
		my $paint_key = 'Paint\\Recent File List';
		my $paint = $key->get_subkey($paint_key);
		if (defined $paint) {
			::rptMsg($key_path."\\".$paint_key);
			::rptMsg("LastWrite Time ".::format8601Date($paint->get_timestamp())."Z");
			
			my @vals = $paint->get_list_of_values();
			if (scalar(@vals) > 0) {
				my %files;
# Retrieve values and load into a hash for sorting			
				foreach my $v (@vals) {
					my $val = $v->get_name();
					my $data = $v->get_data();
					my $tag = (split(/File/,$val))[1];
					$files{$tag} = $val.":".$data;
				}
# Print sorted content to report file			
				foreach my $u (sort {$a <=> $b} keys %files) {
					my ($val,$data) = split(/:/,$files{$u},2);
					::rptMsg("  ".$val." -> ".$data);
				}
			}
			else {
				::rptMsg($key_path."\\".$paint_key." has no values.");
			}			
		}
		else {
			::rptMsg($key_path."\\".$paint_key." not found.");
		}
# Get Last Registry key opened in RegEdit
		my $reg_key = "Regedit";
		my $reg = $key->get_subkey($reg_key);
		if (defined $reg) {
			::rptMsg("");
			::rptMsg($key_path."\\".$reg_key);
			::rptMsg("LastWrite Time ".::format8601Date($reg->get_timestamp())."Z"); 
			eval {
				my $lastkey = $reg->get_value("LastKey")->get_data();
				::rptMsg("RegEdit LastKey value -> ".$lastkey);
			};

# added 20 Oct 2020			
			eval {
				my $fav;
				if ($fav = $reg->get_subkey("Favorites")) {
					my @vals = $fav->get_list_of_values();
					if (scalar @vals > 0) {
						::rptMsg("");
						::rptMsg("RegEdit Favorites");
						foreach my $v (@vals) {
							::rptMsg(sprintf "%-25s %-50s",$v->get_name(),$v->get_data());
						}
					}
				}
			};
		}		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;