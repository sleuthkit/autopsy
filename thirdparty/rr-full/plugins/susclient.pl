#-----------------------------------------------------------
# susclient.pl
#   Values within this key appear to include the hard drive serial number
#
# Change history
#   20201005 - MITRE update
#   20200518 - updated date output format
#   20140326 - created
#
# References
#   Issues with WMI: http://www.techques.com/question/1-10989338/WMI-HDD-Serial-Number-Transposed
#   *command "wmic diskdrive get serialnumber" will return transposed info
#
# Copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package susclient;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "devices",
			  output		=> "report",
              version       => 20201005);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Extracts SusClient* info, including HDD SN (if avail)";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching susclient v.".$VERSION);
  ::rptMsg("susclient v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");      
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $key_path = ("Microsoft\\Windows\\CurrentVersion\\WindowsUpdate");
	
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				if ($v->get_name() eq "LastRestorePointSetTime") {
					::rptMsg(sprintf "%-25s  %-30s",$v->get_name(),$v->get_data());
				}
				elsif ($v->get_name() eq "SusClientId") {
					::rptMsg(sprintf "%-25s  %-30s",$v->get_name(),$v->get_data());
				}
				elsif ($v->get_name() eq "SusClientIdValidation") {
					my $sn = parseSN($v->get_data());
					::rptMsg("SusClientIdValidation - Serial Number: ".$sn);
					::rptMsg("");
					::rptMsg("Analysis Tip: If available, this value may be the HDD serial number.");
				}
				else {}
			}
		}
		else {
			::rptMsg($key_path." has no values\.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

sub parseSN {
	my $data = shift;
	my $sn;
	
	my $offset = unpack("C",substr($data,0,1));
	my $sz     = unpack("C",substr($data,2,1));
	
	$sn = substr($data,$offset,$sz);
	$sn =~ s/\00//g;
	$sn =~ s/\20//g;
	return $sn;
}

1;
