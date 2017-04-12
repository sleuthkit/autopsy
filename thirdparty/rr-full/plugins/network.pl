#-----------------------------------------------------------
# network.pl
# Plugin for Registry Ripper; Get information on network 
# interfaces from the System hive file - from the
# Control\Network GUID subkeys...
# 
# Change history
#
#
# References
#   
# 
# copyright 2008 H. Carvey
#-----------------------------------------------------------
package network;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20080324);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets info from System\\Control\\Network GUIDs";	
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
	::logMsg("Launching network v.".$VERSION);
	::rptMsg("network v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $nw_path = $ccs."\\Control\\Network\\{4D36E972-E325-11CE-BFC1-08002BE10318}";
		my $nw;
		if ($nw = $root_key->get_subkey($nw_path)) {
			::rptMsg("Network key");
			::rptMsg($nw_path);
# Get all of the subkey names
			my @sk = $nw->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my $name = $s->get_name();
					next if ($name eq "Descriptions");
					if (my $conn = $nw->get_subkey($name."\\Connection")) {
						::rptMsg("Interface ".$name);
						::rptMsg("LastWrite time ".gmtime($conn->get_timestamp())." (UTC)");
						my %conn_vals;
						my @vals = $conn->get_list_of_values();
						map{$conn_vals{$_->get_name()} = $_->get_data()}@vals;
						::rptMsg("\tName              = ".$conn_vals{Name});
						::rptMsg("\tPnpInstanceID     = ".$conn_vals{PnpInstanceID});
						::rptMsg("\tMediaSubType      = ".$conn_vals{MediaSubType});
						::rptMsg("\tIpCheckingEnabled = ".$conn_vals{IpCheckingEnabled}) 
							if (exists $conn_vals{IpCheckingEnabled});
						
					}
					::rptMsg("");
				}
				
			}
			else {
				::rptMsg($nw_path." has no subkeys.");
			}			
		}
		else {
			::rptMsg($nw_path." could not be found.");
			::logMsg($nw_path." could not be found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;