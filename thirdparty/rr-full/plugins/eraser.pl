#-----------------------------------------------------------
# eraser.pl
#   Gets Eraser User Settings
#
# Change history
#   20180708 - Created (based on ccleaner.pl plugin)
#
# References
#
# Author: Hadar Yudovich <@hadar0x>
#-----------------------------------------------------------
package eraser;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20180708);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets User's Eraser Settings";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching Eraser v.".$VERSION);
	::rptMsg("Eraser v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive); # creates a Win32Registry object
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Eraser\\Eraser 6";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my %eraserkeys;
		my @eraservals = $key->get_list_of_values();
		if (scalar(@eraservals) > 0) {
			foreach my $val (@eraservals) {
				$eraserkeys{$val->get_name()} = $val->get_data();
			}
			foreach my $keyval (sort keys %eraserkeys) {
				::rptMsg($keyval." -> ".$eraserkeys{$keyval});
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." does not exist.");
	}
	::rptMsg("");
}

1;
