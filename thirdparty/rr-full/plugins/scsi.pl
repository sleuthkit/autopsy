#-----------------------------------------------------------
# scsi.pl 
# Parses contents of Enum\SCSI
# 
# History
#   20220802 - copied from usbstor.pl
#
# References:
#	
#
# copyright 2022 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package scsi;
use strict;

my %config = (hive          => "System",
              MITRE         => "",
              category      => "devices",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20220802);

sub getConfig{return %config}

sub getShortDescr {
	return "Parses Enum\\SCSI key";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $reg;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	$reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	::logMsg("Launching scsi v.".$VERSION);
	::rptMsg("scsi v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr());
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");

	my $key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Enum\\SCSI";
	my $key;
	
	my @vals = ("DeviceDesc","Mfg","Service","FriendlyName");
	
	if ($key = $root_key->get_subkey($key_path)) {
		
		my @subkeys = $key->get_list_of_subkeys();
		if (scalar @subkeys > 0) {
			foreach my $s (@subkeys) {
				::rptMsg($s->get_name());
				my @sk = $s->get_list_of_subkeys();
				if (scalar @sk > 0) {
					foreach my $k (@sk) {
						::rptMsg("  ".$k->get_name());
						
						foreach my $v (@vals) {
							eval {
								my $x = $k->get_value($v)->get_data();
								::rptMsg(sprintf "    %-15s: %-30s",$v,$x);
							};
						}
# get Properties\{83da6326-97a6-4088-9453-a1923f573b29}						
						eval {
							getProperties($k->get_subkey("Properties\\{83da6326-97a6-4088-9453-a1923f573b29}"));
						};
					}
				}
				::rptMsg("");
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


sub getProperties {
	my $key = shift;

	eval {
		my $r = $key->get_subkey("0064")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg(sprintf "    %-15s: %-25s","First Install",::format8601Date($t)."Z");
	};

	eval {
		my $r = $key->get_subkey("0065")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg(sprintf "    %-15s: %-25s","First Inserted",::format8601Date($t)."Z");
	};
	
	eval {
		my $r = $key->get_subkey("0066")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg(sprintf "    %-15s: %-25s","Last Inserted",::format8601Date($t)."Z");
	};
	
	eval {
		my $r = $key->get_subkey("0067")->get_value("")->get_data();
		my ($t0,$t1) = unpack("VV",$r);
		my $t = ::getTime($t0,$t1);
		::rptMsg(sprintf "    %-15s: %-25s","Last Removal",::format8601Date($t)."Z");
	};


}


1;