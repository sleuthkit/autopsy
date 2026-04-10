#-----------------------------------------------------------
# location
#
# Change history:
#  20211116 - created
# 
# Ref:
#  
#
# copyright 2021 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package location;
use strict;

my %config = (hive          => "NTUSER\.DAT",
			  category      => "user activity",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20211116);

sub getConfig{return %config}
sub getShortDescr {
	return "Get apps that use Location services";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching location v.".$VERSION);
	::rptMsg("location v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\location\\NonPackaged';
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
	
		eval {
			my $val = $key->get_value("Value")->get_data();
			::rptMsg("location key Value value: ".$val);
			::rptMsg("");
		};
	
	
		my @sk1 = $key->get_list_of_subkeys();
		if (scalar @sk1 > 0) {
			foreach my $s1 (@sk1) {
				processKey($s1);
			}
		}
		else {
			::rptMsg($key_path." has no subkeys.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: This plugin provides info about non-packaged apps that make use of location servers, as well as whether");
	::rptMsg("location services are allowed or denied.");
}

sub processKey {
	my $key = shift;
	my $name = $key->get_name();
	
	my $start = ();
	my $stop  = ();
						
	eval {
		my $s = $key->get_value("LastUsedTimeStart")->get_data();
		my ($t0,$t1) = unpack("VV",$s);
		$start = ::getTime($t0,$t1);
	};
							
	eval {
		my $s = $key->get_value("LastUsedTimeStop")->get_data();
		my ($t0,$t1) = unpack("VV",$s);
		$stop = ::getTime($t0,$t1);
	};
	
	
	if ($start && $stop) {
		::rptMsg($name);
		::rptMsg(sprintf "%-20s %-20s","LastWrite time",::format8601Date($key->get_timestamp())."Z");
		::rptMsg(sprintf "%-20s %-20s","LastUsedTimeStart",::format8601Date($start)."Z");
		::rptMsg(sprintf "%-20s %-20s","LastUsedTimeStop",::format8601Date($stop)."Z");
		::rptMsg("");
	}
}

1;