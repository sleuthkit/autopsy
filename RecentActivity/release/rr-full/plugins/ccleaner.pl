#-----------------------------------------------------------
# ccleaner.pl
#   Gets CCleaner User Settings
#
# Change history
#   20120128 [ale] % Initial Version based on warcraft3.pl plugin
#
# References
#
# Author: Adrian Leong <cheeky4n6monkey@gmail.com>
#-----------------------------------------------------------
package ccleaner;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120128);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets User's CCleaner Settings";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift; # pops the first element off @_ ie the parameter array passed in to pluginmain 
	my $hive = shift; # 1st element in @_ is class/package name (ccleaner), 2nd is the hive name passed in from rip.pl 
	::logMsg("Launching ccleaner v.".$VERSION);
	::rptMsg("ccleaner v.".$VERSION);
	::rptMsg("(".getHive().") ".getShortDescr()."\n");
	my $reg = Parse::Win32Registry->new($hive); # creates a Win32Registry object 
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = "Software\\Piriform\\CCleaner";
	# If CCleaner key_path exists ... ie get_subkey returns a non-empty value
	if ($key = $root_key->get_subkey($key_path)) {
		# Print registry key name and last modified date 
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my %cckeys; # temporary associative array for storing name / value pairs eg ("UpdateCheck", 1)
		# Extract ccleaner key values into ccvals array 
		# Note: ccvals becomes an array of "Parse::Win32Registry::WinNT::Value"
		# As this is implemented in an Object oriented manner, we cannot access the values directly -
		# we have to use the "get_name" and "get_value" subroutines
		my @ccvals = $key->get_list_of_values();
		# If ccvals has any "Values" in it, call "Value::get_name" and "Value::get_data" for each 
		# and store the results in the %cckeys associative array using data returned by Value::get_name as the id/index
		# and Value::get_data for the actual key value
		if (scalar(@ccvals) > 0) {
			foreach my $val (@ccvals) {
				$cckeys{$val->get_name()} = $val->get_data();
			}
			# Sorts keynames into a temp list and then prints each key name + value in list order
			# the values are retrieved from cckeys assoc. array which was populated in the previous foreach loop
			foreach my $keyval (sort keys %cckeys) {
				::rptMsg($keyval." -> ".$cckeys{$keyval});
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." does not exist.");
	}
	# Return obligatory new-line
	::rptMsg("");
}

1;
