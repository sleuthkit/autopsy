#-----------------------------------------------------------
# specaccts.pl
# Gets contents of SpecialAccounts\UserList key
# 
# History
#   20100223 - created
#
# References
#   http://www.microsoft.com/security/portal/Threat/Encyclopedia/
#          Entry.aspx?Name=Trojan%3AWin32%2FStarter
#
#   http://www.microsoft.com/Security/portal/Threat/Encyclopedia/
#          Entry.aspx?Name=TrojanSpy%3AWin32%2FUrsnif.gen!H&ThreatID=-2147343835
#
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package specaccts;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100223);

sub getConfig{return %config}

sub getShortDescr {
	return "Gets contents of SpecialAccounts\\UserList key";	
}
sub getDescr{}

sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching specaccts v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\SpecialAccounts\\UserList";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my %apps;
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-20s 0x%x",$v->get_name(),$v->get_data());
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;