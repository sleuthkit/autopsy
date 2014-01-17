#-----------------------------------------------------------
# networklist_tln.pl - Plugin to extract information from the 
#   NetworkList key, including the MAC address of the default
#   gateway
#
#
# Change History:
#    20120608 - updated from networklist.pl to add TLN output
#    20090812 - updated code to parse DateCreated and DateLastConnected
#               values; modified output, as well
#    20090811 - created
#
# References
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package networklist_tln;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20120608);

sub getConfig{return %config}

sub getShortDescr {
	return "Collects network info from NetworkList key (TLN)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %types = (0x47 => "wireless",
             0x06 => "wired",
             0x17 => "broadband (3g)");

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching networklist_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $base_path = "Microsoft\\Windows NT\\CurrentVersion\\NetworkList";
	
# First, get profile info	
	my $key_path = $base_path."\\Profiles";
	my $key;
	my %nl; # hash of hashes to hold data
	if ($key = $root_key->get_subkey($key_path)) {
#		::rptMsg($key_path);
	
		my @sk = $key->get_list_of_subkeys();
		if (scalar(@sk) > 0) {
			foreach my $s (@sk) {
				my $name = $s->get_name();
				$nl{$name}{LastWrite} = $s->get_timestamp();
				eval {
					$nl{$name}{ProfileName} = $s->get_value("ProfileName")->get_data();
					$nl{$name}{Description} = $s->get_value("Description")->get_data();
					$nl{$name}{Managed} = $s->get_value("Managed")->get_data();
					
					my $create = $s->get_value("DateCreated")->get_data();
					$nl{$name}{DateCreated} = parseDate128($create) if (length($create) == 16);
					my $conn   = $s->get_value("DateLastConnected")->get_data();
					$nl{$name}{DateLastConnected} = parseDate128($conn) if (length($conn) == 16);
					
#					$nl{$name}{NameType} = $s->get_value("ProfileName")->get_data();

					$nl{$name}{NameType} = $s->get_value("NameType")->get_data();
					
					if (exists $types{$nl{$name}{NameType}}) {
						$nl{$name}{Type} = $types{$nl{$name}{NameType}};
					}
					else {
						$nl{$name}{Type} = $nl{$name}{NameType};
					}
				};
			}

# Get additional information from the Signatures subkey
			$key_path = $base_path."\\Signatures\\Managed";
			if ($key = $root_key->get_subkey($key_path)) { 
				my @sk = $key->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $s (@sk) {
						eval {
							my $prof = $s->get_value("ProfileGuid")->get_data();
							my $tmp = substr($s->get_value("DefaultGatewayMac")->get_data(),0,6);
							my $mac = uc(unpack("H*",$tmp));
							my @t = split(//,$mac);
							$nl{$prof}{DefaultGatewayMac} = $t[0].$t[1]."-".$t[2].$t[3].
							         "-".$t[4].$t[5]."-".$t[6].$t[7]."-".$t[8].$t[9]."-".$t[10].$t[11];
						};
					}
				}
			}
		
			$key_path = $base_path."\\Signatures\\Unmanaged";
			if ($key = $root_key->get_subkey($key_path)) { 
				my @sk = $key->get_list_of_subkeys();
				if (scalar(@sk) > 0) {
					foreach my $s (@sk) {
						eval {
							my $prof = $s->get_value("ProfileGuid")->get_data();
							my $tmp = substr($s->get_value("DefaultGatewayMac")->get_data(),0,6);
							my $mac = uc(unpack("H*",$tmp));
							my @t = split(//,$mac);
							$nl{$prof}{DefaultGatewayMac} = $t[0].$t[1]."-".$t[2].$t[3].
							         "-".$t[4].$t[5]."-".$t[6].$t[7]."-".$t[8].$t[9]."-".$t[10].$t[11]; 
						};
					}
				}
			}
			
# Now, display the information			
			foreach my $n (keys %nl) {
				my $str = sprintf "%-15s Gateway Mac: ".$nl{$n}{DefaultGatewayMac},$nl{$n}{ProfileName};
#				::rptMsg($nl{$n}{ProfileName});
#				::rptMsg("  Key LastWrite    : ".gmtime($nl{$n}{LastWrite})." UTC");
#				::rptMsg("  DateLastConnected: ".$nl{$n}{DateLastConnected});
#				::rptMsg("  DateCreated      : ".$nl{$n}{DateCreated});
#				::rptMsg("  DefaultGatewayMac: ".$nl{$n}{DefaultGatewayMac});
#				::rptMsg("");
				
				::rptMsg($nl{$n}{LastWrite}."|REG|||[".$nl{$n}{Type}." Connect] - Last Connected to ".$nl{$n}{ProfileName}." (".$nl{$n}{DefaultGatewayMac}.")");
			}		
		}
		else {
#			::rptMsg($key_path." has not subkeys");
		}
	}
	else {
#		::rptMsg($key_path." not found.");
	}
}

sub parseDate128 {
	my $date = $_[0];
	my @months = ("Jan","Feb","Mar","Apr","May","Jun","Jul",
	              "Aug","Sep","Oct","Nov","Dec");
	my @days = ("Sun","Mon","Tue","Wed","Thu","Fri","Sat");
	my ($yr,$mon,$dow,$dom,$hr,$min,$sec,$ms) = unpack("v*",$date);
	$hr = "0".$hr if ($hr < 10);
	$min = "0".$min if ($min < 10);
	$sec = "0".$sec if ($sec < 10);
	my $str = $days[$dow]." ".$months[$mon - 1]." ".$dom." ".$hr.":".$min.":".$sec." ".$yr;
	return $str;
}
1;