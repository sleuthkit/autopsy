#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# userassist.pl
# Plugin for Registry Ripper, NTUSER.DAT edition - gets the 
# UserAssist values 
#
# Change history
#  20080726 - added reference to help examiner understand Control
#             Panel entries found in output
#  20080301 - updated to include run count along with date
#
# 
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package userassist;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20080726);

sub getConfig{return %config}
sub getShortDescr {
	return "Displays contents of UserAssist Active Desktop key";	
}
sub getDescr{}
sub getRefs {"Description of Control Panel Files in XP" => "http://support.microsoft.com/kb/313808"}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching UserAssist (Active Desktop) v.".$VERSION);
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\UserAssist\\'.
	               '{75048700-EF1F-11D0-9888-006097DEACF9}\\Count';
	my $key;
	my %ua;
	my $hrzr = "HRZR";
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("UserAssist (Active Desktop)");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $value_name = $v->get_name();
				my $data = $v->get_data();
				if (length($data) == 16) {
					my ($session,$count,$val1,$val2) = unpack("V*",$data);
				 	if ($val2 != 0) {
						my $time_value = ::getTime($val1,$val2);
						if ($value_name =~ m/^$hrzr/) { 
							$value_name =~ tr/N-ZA-Mn-za-m/A-Za-z/;
						}
						$count -= 5 if ($count > 5);
						push(@{$ua{$time_value}},$value_name." (".$count.")");
					}
				}
			}
			foreach my $t (reverse sort {$a <=> $b} keys %ua) {
				::rptMsg(gmtime($t)." (UTC)");
				foreach my $item (@{$ua{$t}}) {
					::rptMsg("\t$item");
				}
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