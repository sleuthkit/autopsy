#-----------------------------------------------------------
# gauss.pl
# Checks Software hive for existance of TimeStampforUI value
# beneath the Reliability key within the Software hive.  According
# to the Kasperky write-up for the malware, the configuration file is
# written to a binary value named "TimeStampforUI".
#
# copyright 2012 Quantum Analytics Research, LLC
# Author H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package gauss;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20120809);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks Reliability key for TimeStampforUI value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching gauss v.".$VERSION);
	::rptMsg("Launching gauss v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @key_paths = ('Microsoft\\Windows\\CurrentVersion\\Reliability',
	                 'Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Reliability');
	::rptMsg("gauss v\.".$VERSION);
	foreach my $key_path (@key_paths) {
		my $key;
		my $notfound = 1;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			my @vals = $key->get_list_of_values();
			foreach my $v (@vals) {
				my $name = $v->get_name();
				if ($name eq "TimeStampforUI") {
					::rptMsg("TimeStampforUI value found.");
					$notfound = 0;
				}
			}
			::rptMsg("TimeStampforUI value not found.") if ($notfound);
		}
		else {
			::rptMsg($key_path." not found.");
		}
		::rptMsg("");
	}
}
1;