#-----------------------------------------------------------
# lsa_packages.pl
# 
#
# Change history
#   20140730 - added "EveryoneIncludesAnonymous"
#   20130307 - created
# 
# Reference: 
#   http://carnal0wnage.attackresearch.com/2013/09/stealing-passwords-every-time-they.html
#
# Category: Autostart
# 
#
# copyright 2014 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package lsa_packages;

my %config = (hive          => "System",
              hasShortDescr => 1,
              category      => "malware",
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20140730);

sub getConfig{return %config}
sub getShortDescr {
	return "Lists various *Packages key contents beneath LSA key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

my @pkgs = ("Authentication Packages", "Notification Packages", "Security Packages",
            "EveryoneIncludesAnonymous");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching lsa_packages v.".$VERSION);
	::rptMsg("lsa_packages v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key();
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
		
		$key_path = $ccs.'\\Control\\LSA';
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp())." UTC");
			::rptMsg("");
			
			foreach my $v (@pkgs) {
				eval {
					my $d = $key->get_value($v)->get_data();
					::rptMsg(sprintf "%-23s: ".$d,$v);
				};
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;