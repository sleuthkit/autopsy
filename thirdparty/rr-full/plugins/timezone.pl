#-----------------------------------------------------------
# timezone.pl
# Plugin for Registry Ripper; Access System hive file to get the
# contents of the TimeZoneInformation key
# 
# Change history
#   20130830 - updated
#   20080324 - created
#
# References
#   http://support.microsoft.com/kb/102986
#   http://support.microsoft.com/kb/207563
#   http://msdn.microsoft.com/en-us/library/windows/desktop/ms725481(v=vs.85).aspx
#   
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package timezone;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130830);

sub getConfig{return %config}
sub getShortDescr {
	return "Get TimeZoneInformation key contents";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching timezone v.".$VERSION);
	::rptMsg("timezone v.".$VERSION); # banner
   ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		my $tz_path = $ccs."\\Control\\TimeZoneInformation";
		my $tz;
		if ($tz = $root_key->get_subkey($tz_path)) {
			::rptMsg("TimeZoneInformation key");
			::rptMsg($tz_path);
			::rptMsg("LastWrite Time ".gmtime($tz->get_timestamp())." (UTC)");
			my %tz_vals;
			my @vals = $tz->get_list_of_values();
			if (scalar(@vals) > 0) {
				map{$tz_vals{$_->get_name()} = $_->get_data()}(@vals);
				
				::rptMsg("  DaylightName   -> ".$tz_vals{"DaylightName"});
				::rptMsg("  StandardName   -> ".$tz_vals{"StandardName"});
				
				my $a = unpack("s",pack("S",$tz_vals{"ActiveTimeBias"}));
				my $b = unpack("s",pack("S",$tz_vals{"Bias"}));

				::rptMsg("  Bias           -> ".$b." (".($b/60)." hours)");
				::rptMsg("  ActiveTimeBias -> ".$a." (".($a/60)." hours)");
				
			}
			else {
				::rptMsg($tz_path." has no values.");
			}
		}
		else {
			::rptMsg($tz_path." could not be found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;