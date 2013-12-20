#-----------------------------------------------------------
# nic.pl
# 
# 
# Change history
#    20100401 - created
#
# References
#   LeaseObtainedTime - http://technet.microsoft.com/en-us/library/cc978465.aspx
#   T1 - http://technet.microsoft.com/en-us/library/cc978470.aspx
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package nic;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100401);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets NIC info from System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %nics;
	my $ccs;
	::logMsg("Launching nic v.".$VERSION);
	::rptMsg("nic v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	eval {
		$current = $root_key->get_subkey("Select")->get_value("Current")->get_data();
	};
	my @nics;
	my $key_path = "ControlSet00".$current."\\Services";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @svcs = $key->get_list_of_subkeys();
		foreach my $s (@svcs) {
			push(@nics,$s) if ($s->get_name() =~ m/^{/);
		}
		foreach my $n (@nics) {
			eval {
				my @vals = $n->get_subkey("Parameters\\Tcpip")->get_list_of_values();
				::rptMsg("Adapter: ".$n->get_name());
				::rptMsg("LastWrite Time: ".gmtime($n->get_timestamp())." Z");
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $data = $v->get_data();
					$data = gmtime($data)." Z" if ($name eq "T1" || $name eq "T2");
					$data = gmtime($data)." Z" if ($name =~ m/Time$/);
					
					::rptMsg(sprintf "  %-20s %-20s",$name,$data);
					
				}
				::rptMsg("");
			};
		}	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;