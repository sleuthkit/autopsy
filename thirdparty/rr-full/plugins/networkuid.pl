#-----------------------------------------------------------
# networkuid.pl
# Gets UID value from Network key
# 
# References
#   http://blogs.technet.com/mmpc/archive/2010/03/11/got-zbot.aspx
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package networkuid;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100312);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets Network key UID value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networkuid v.".$VERSION);
	::rptMsg("networkuid v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Network";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite time = ".gmtime($key->get_timestamp()));
		::rptMsg("");
		
		eval {
			my $uid = $key->get_value("UID")->get_data();
			::rptMsg("UID value = ".$uid);
		};
		::rptMsg("UID value not found.") if ($@);
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;