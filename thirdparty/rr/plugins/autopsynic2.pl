#-----------------------------------------------------------
# autopsynic2.pl
# 
# Change history
#    20181024 - modifications for outputting into format for Autopsy
#    20150812 - included updates from Yogesh Khatri
#    20100401 - created
#
# References
#   LeaseObtainedTime - http://technet.microsoft.com/en-us/library/cc978465.aspx
#   T1 - http://technet.microsoft.com/en-us/library/cc978470.aspx
# 
# copyright 2015 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package autopsynic2;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20150812);

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
	#::logMsg("Launching nic2 v.".$VERSION);
	#::rptMsg("nic2 v.".$VERSION); # banner
    #::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::rptMsg("<SSID>");
	::rptMsg("<mtime></mtime>");
	::rptMsg("<artifacts>");
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $current;
	eval {
		$current = $root_key->get_subkey("Select")->get_value("Current")->get_data();
	};
	my @nics;
	my $key_path = "ControlSet00".$current."\\Services\\Tcpip\\Parameters\\Interfaces";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my @guids = $key->get_list_of_subkeys();
		if (scalar @guids > 0) {
			foreach my $g (@guids) {
				my $adapterName = $g->get_name();
				#::rptMsg("Adapter: ".$g->get_name());
				my $lastWriteTime = $g->get_timestamp();
				#::rptMsg("LastWrite Time: ".gmtime($g->get_timestamp())." Z");
				eval {
					my @vals = $g->get_list_of_values();
					foreach my $v (@vals) {
						my $name = $v->get_name();
						my $data = $v->get_data();
						$data = gmtime($data)." Z" if ($name eq "T1" || $name eq "T2");
						$data = gmtime($data)." Z" if ($name =~ m/Time$/);
						$data = pack("h*",reverse $data) if (uc($name) eq uc("DhcpNetworkHint")); # SSID nibbles reversed //YK
						#::rptMsg(sprintf "  %-28s %-20s",$name,$data);
					}
					#::rptMsg("");
				};
				# Parse subfolders having similar data for different wifi access points , key name is SSID (nibbles reversed) //YK
				my @ssids = $g->get_list_of_subkeys();
				if (scalar @ssids > 0) {
					foreach my $ssid (@ssids) {
						#::rptMsg("Adapter: ".$g->get_name()."/".$ssid->get_name());
						my $ssid_realname = pack("h*",reverse $ssid->get_name());
						#::rptMsg("SSID Decoded: ".$ssid_realname);
						::rptMsg("<ssid adapter=\"". $adapterName ."\" writeTime=\"".$lastWriteTime."\">".$ssid_realname."</ssid>");
						#::rptMsg("LastWrite Time: ".gmtime($ssid->get_timestamp())." Z");
						eval {
							my @vals = $ssid->get_list_of_values();
							foreach my $v (@vals) {
								my $name = $v->get_name();
								my $data = $v->get_data();
								$data = gmtime($data)." Z" if ($name eq "T1" || $name eq "T2");
								$data = gmtime($data)." Z" if ($name =~ m/Time$/);
								$data = pack("h*",reverse $data) if (uc($name) eq uc("DhcpNetworkHint"));
								#::rptMsg(sprintf "  %-28s %-20s",$name,$data);
							}
							#::rptMsg("");
						};
					}
				}
				else {
					#::rptMsg($key_path." has no subkeys.");
				}	
			}
		}
		else {
			#::rptMsg($key_path." has no subkeys.");
		}	
	}
	else {
		#::rptMsg($key_path." not found.");
	}
	::rptMsg("</artifacts></SSID>");
}
1;