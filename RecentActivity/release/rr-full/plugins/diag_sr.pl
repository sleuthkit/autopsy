#-----------------------------------------------------------
# diag_sr.pl
#
# History:
#  20120515: created
#
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package diag_sr;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120515);

sub getConfig{return %config}
sub getShortDescr {
	return "Get Diag\\SystemRestore values and data";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching diag_sr v.".$VERSION);
	::rptMsg("diag_sr v.".$VERSION); # banner
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $volsnap_path = $ccs."\\Services\\VSS\\Diag\\SystemRestore";
		my $volsnap;
		if ($volsnap = $root_key->get_subkey($volsnap_path)) {
			my @vals = $volsnap->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					my $name = $v->get_name();
					my $t = gmtime(parseData($v->get_data()));
					
					::rptMsg(sprintf "%-25s  %-50s",$t,$name);
					
				}
			}
			else {
				::rptMsg($volsnap_path." has no values.");
			}
		}
		else {
			::rptMsg($volsnap_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseData {
	my $data = shift;
	my ($t0,$t1) = unpack("VV",substr($data,0x08,8));
	return ::getTime($t0,$t1);	
}

1;