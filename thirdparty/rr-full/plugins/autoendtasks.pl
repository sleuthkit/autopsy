#-----------------------------------------------------------
# autoendtasks.pl
#
# History
#   20081128 - created
# 
# Ref: 
#   http://support.microsoft.com/kb/555619
#   This Registry setting tells XP (and Vista) to automatically
#   end non-responsive tasks; value may not exist on Vista.
#
# copyright 2008 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package autoendtasks;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081128);

sub getConfig{return %config}

sub getShortDescr {
	return "Automatically end a non-responsive task";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching autoendtasks v.".$VERSION);
	::rptMsg("autoendtasks v.".$VERSION); # banner
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Control Panel\\Desktop';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg("autoendtasks");
		::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $autoend;
		eval {
			$autoend = $key->get_value("AutoEndTasks")->get_data();
		};
		if ($@) {
			::rptMsg("AutoEndTasks value not found.");
		}
		else {
			::rptMsg("AutoEndTasks = ".$autoend);
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;