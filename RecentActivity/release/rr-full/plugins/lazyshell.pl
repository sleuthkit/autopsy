#-----------------------------------------------------------
# lazyshell
#
# Change history:
#  20131007 - created
# 
# Ref:
#  
#
# copyright 2013 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lazyshell;
use strict;

my %config = (hive          => "Software",
							category      => "malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20131007);

sub getConfig{return %config}
sub getShortDescr {
	return "Checks for keys/values assoc. with LazyShell";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching lazyshell v.".$VERSION);
	::rptMsg("lazyshell v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my @paths = ('Microsoft\\Windows\\CurrentVersion\\Wordpad\\ComChecks\\Safelist',
	         'Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Wordpad\\ComChecks\\Safelist');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			
			eval {
				my $cc = $key->get_value("CategoryCount")->get_data();
				::rptMsg("CategoryCount value found\.");
			};
			
			eval {
				my $r = $key->get_value("ResetAU")->get_data();
				::rptMsg("ResetAU value found\.");
			};
			::rptMsg("");
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}
1;