#-----------------------------------------------------------
# proxysettings.pl
# Plugin for Registry Ripper,
# Internet Explorer ProxySettings key parser
#
# Change history
#    20081224 - H. Carvey, updated sorting and printing routine
#
#
# copyright 2008 C. Bentley
#-----------------------------------------------------------
package proxysettings;
use strict;

my %config = (hive => "NTUSER\.DAT",
							hasShortDescr => 1,
							hasDescr => 0,
							hasRefs => 0,
							osmask => 22,
							version => 20081224);

sub getConfig{return %config}
sub getShortDescr {return "Gets contents of user's Proxy Settings";}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching proxysettings v.".$VERSION);
	::rptMsg("proxysettings v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;

	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("ProxySettings");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			my %proxy;
			foreach my $v (@vals) {
				my $name = $v->get_name();
				my $data = $v->get_data();
				my $type = $v->get_type();
				$data = unpack("V",$data) if ($type == 3);
				$proxy{$name} = $data;
			}
			foreach my $n (sort keys %proxy) {
			 	my $str = sprintf "  %-30s %-30s",$n,$proxy{$n};
				::rptMsg($str);
#				::rptMsg(" ".$v->get_name()." ".$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." key has no values.");
			::logMsg($key_path." key has no values.");
		}
	}
	else {
		::rptMsg($key_path." hat key not found.");
		::logMsg($key_path." hat key not found.");
	}
}
1; 