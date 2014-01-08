#-----------------------------------------------------------
# routes.pl
#
# Some malware is known to create persistent routes
#
# Change History:
#  20100817 - created
#	
# Ref: 
#  http://support.microsoft.com/kb/141383
#  http://www.symantec.com/security_response/writeup.jsp?docid=
#         2010-041308-3301-99&tabid=2
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package routes;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20100817);

sub getConfig{return %config}

sub getShortDescr {
	return "Get persistent routes";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching routes v.".$VERSION);
	::rptMsg("routes v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		my $ccs = "ControlSet00".$current;
	
		my $sb_path = $ccs."\\Services\\Tcpip\\Parameters\\PersistentRoutes";
		
		my $sb;
		if ($sb = $root_key->get_subkey($sb_path)) {
			::rptMsg($sb_path);
			::rptMsg("LastWrite: ".gmtime($sb->get_timestamp()));
			::rptMsg("");
			my @vals = $sb->get_list_of_values();
			
			if (scalar(@vals) > 0) {
				::rptMsg(sprintf "%-15s  %-15s %-15s %-5s","Address","Netmask","Gateway","Metric");
				foreach my $v (@vals) {
					my ($addr,$netmask,$gateway,$metric) = split(/,/,$v->get_name(),4);
					::rptMsg(sprintf "%-15s  %-15s %-15s %-5s",$addr,$netmask,$gateway,$metric);
				}
			}
			else {
				::rptMsg($sb_path." has no values.");
			}
		}
		else {
			::rptMsg($sb_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;