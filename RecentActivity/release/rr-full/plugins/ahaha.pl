#-----------------------------------------------------------
# ahaha.pl - plugin to detect possible presence of Ahaha backdoor 
#   
# Change history
#   20131009 - created
#
# References
#   http://www.microsoft.com/security/portal/threat/encyclopedia/Entry.aspx?Name=Adware%3AWin32%2FOpenCandy#tab=2
#
# Copyright (c) 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ahaha;
use strict;

my %config = (hive          => "Software,NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "malware",
              version       => 20131009);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Detect possible presence of ahaha malware";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching ahaha v.".$VERSION);
  ::rptMsg("ahaha v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $count = 0;
	
	my @paths = ("Microsoft\\Windows\\CurrentVersion\\Run",
	             "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run",
# Check NTUSER.DAT hive	             
	             "Software\\Microsoft\\Windows\\CurrentVersion\\Run",
	             "Software\\Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Run");
	
	foreach my $key_path (@paths) {
		if ($key = $root_key->get_subkey($key_path)) {
			my @vals = $key->get_list_of_values();
			if (scalar @vals > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					
					if ($name eq "360v") {
						::rptMsg("Possible Backdoor\.Ahaha found\.");
						$count = 1;
					}
					my $lcdata = $data;
					$lcdata =~ tr/[A-Z]/[a-z]/;
					if (grep(/appdata/,$lcdata) || grep(/application data/,$lcdata)) {
						::rptMsg("Path includes %AppData%: ".$data);
						$count = 1;
					}
				}
			}
		}
		
	}
	
	if ($count == 0) {
		::rptMsg("No indicators found\.");
	}
	
}

1;
