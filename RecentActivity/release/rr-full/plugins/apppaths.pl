#-----------------------------------------------------------
# apppaths
# Gets contents of App Paths subkeys from the Software hive,
# diplaying the EXE name and path; all entries are sorted by
# LastWrite time
# 
# References
#
#
# History:
#  20120524 - updated to include 64-bit OSs
#  20080404 - created
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package apppaths;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              version       => 20120524);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets content of App Paths subkeys";	
}
sub getDescr{}
sub getRefs {
	my %refs = ("You cannot open Help and Support Center in Windows XP" => 
	            "http://support.microsoft.com/kb/888018",
	            "Another installation program starts..." => 
	            "http://support.microsoft.com/kb/888470");	
	return %refs;
}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching apppaths v.".$VERSION);
	::rptMsg("apppaths v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# used a list of values to address the need for parsing the App Paths key
# in the Wow6432Node key, if it exists.
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\App Paths",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\App Paths");
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("App Paths");
			::rptMsg($key_path);
			::rptMsg("");
			my %apps;
			my @subkeys = $key->get_list_of_subkeys();
			if (scalar(@subkeys) > 0) {
				foreach my $s (@subkeys) {
				
					my $name = $s->get_name();
					my $lastwrite = $s->get_timestamp();
					my $path;
					eval {
						$path = $s->get_value("")->get_data();
					};
					push(@{$apps{$lastwrite}},$name." - ".$path);
				}
			
				foreach my $t (reverse sort {$a <=> $b} keys %apps) {
					::rptMsg(gmtime($t)." (UTC)");
					foreach my $item (@{$apps{$t}}) {
						::rptMsg("  $item");
					}
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
}
1;