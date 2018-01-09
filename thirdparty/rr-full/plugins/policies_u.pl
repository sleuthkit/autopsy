#-----------------------------------------------------------
# policies_u
# Get values from user's WinLogon key
#
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package policies_u;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20091021);

sub getConfig{return %config}

sub getShortDescr {
	return "Get values from the user's Policies key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching policies_u v.".$VERSION);
	::rptMsg("policies_u v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = "Software\\Microsoft\\Windows\\CurrentVersion";
	my $key;
	if ($key = $root_key->get_subkey($key_path."\\policies")) {
#		::rptMsg("policies key found.");
		
	}
	elsif ($key = $root_key->get_subkey($key_path."\\Policies")) {
#		::rptMsg("Policies key found.");
		
	}
	else {
		::rptMsg("Neither policies nor Policies key found.");
		return;
	}
	
	eval {
		my @vals = $key->get_subkey("Explorer")->get_list_of_values();
		if (scalar(@vals) > 0) {
			::rptMsg("");
			::rptMsg("Explorer subkey values:");
			foreach my $v (@vals) {
				my $str = sprintf "%-20s %-20s",$v->get_name(),$v->get_data();
				::rptMsg("  ".$str);
			}
		}
	};
	::rptMsg("");
	eval {
		my $quota = $key->get_subkey("System")->get_value("EnableProfileQuota")->get_data();
		::rptMsg("EnableProfileQuota = ".$quota);
		::rptMsg("");
		::rptMsg("The EnableProfileQuota = 1 setting causes the proquota\.exe to be run");
		::rptMsg("automatically in order to limit the size of roaming profiles\.  This");
		::rptMsg("corresponds to the Limit Profile Size GPO setting\.");
	};
	::rptMsg("System\\EnableProfileQuota value not found\.") if ($@);
}

1;