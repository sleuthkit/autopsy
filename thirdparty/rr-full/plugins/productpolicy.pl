#-----------------------------------------------------------
# productpolicy.pl
#
# History:
#  20230804 - created
#
# References:
#   https://twitter.com/0gtweet/status/1687353033716273152
#
# Note: all of the values from the ProductPolicy value, and their data, are parsed into a
# Perl hash; that way, if any new values are found at a later date, they can also be extracted
# 
# copyright 2023 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package productpolicy;
use strict;

my %config = (hive          => "system",
			  output        => "report",
			  category      => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",  
              version       => 20230804);

sub getConfig{return %config}
sub getShortDescr {
	return "Get entries from ProductPolicy value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;
my @temps;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching productpolicy v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $ccs = ::getCCS($root_key);
	my $key_path = $ccs."\\Control\\ProductOptions";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		
		eval {
			my $p = $key->get_value("ProductPolicy")->get_data();
#			::probe($p);
			my %policy = processData($p);
			if (exists $policy{"Security-SPP-LastWindowsActivationTime"}) {
				::rptMsg("");
				my ($t0,$t1) = unpack("VV",$policy{"Security-SPP-LastWindowsActivationTime"});
				::rptMsg("Security-SPP-LastWindowsActivationTime : ".::format8601Date(::getTime($t0,$t1))."Z");
				::rptMsg("");
				::rptMsg("Analysis Tip: Grzegorz/\@0gtweet discovered this data embedded in the ProductPolicy value; it may be");
				::rptMsg("useful in determining the lifetime of the endpoint.");
				::rptMsg("");
				::rptMsg("Ref: https://twitter.com/0gtweet/status/1687353033716273152 ");
			}
		};

	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub processData {
	my $data = shift;
	my $totSz = unpack("V",substr($data,0,4));
	my $ofs  = 0x14;
	my %pol = ();
	
	while ($ofs < $totSz) {
		my $eSz      = unpack("v",substr($data,$ofs,2));
		my $eNameSz  = unpack("v",substr($data,$ofs + 2,2));
		my $eDataSz  = unpack("v",substr($data,$ofs + 6,2));
		my $name     = substr($data,$ofs + 0x10,$eNameSz);
		$name =~ s/\00//g;
		
		my $blob = substr($data,$ofs + 0x10 + $eNameSz,$eDataSz);
#		::rptMsg(sprintf "Section size : 0x%x",$eSz);
#		::rptMsg(sprintf "Name size    : 0x%x",$eNameSz);
#		::rptMsg(sprintf "Data size    : 0x%x",$eDataSz);
#		::rptMsg("Name : ".$name);
#		::rptMsg("");
#		::probe($data);
#		::rptMsg("");
		$pol{$name} = $blob;
		$ofs += $eSz;
	}
	return %pol;
}

1;