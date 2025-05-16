#-----------------------------------------------------------
# srum
#
# Change history:
#  20201005 - MITRE update
#  20200518 - minor updates
#  20150721 - created
# 
# Ref:
#  https://files.sans.org/summit/Digital_Forensics_and_Incident_Response_Summit_2015/PDFs/Windows8SRUMForensicsYogeshKhatri.pdf
#
# copyright 2020 QAR,LLC 
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package srum;
use strict;

my %config = (hive          => "Software",
			  category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
			  output		=> "report",
              version       => 20201005);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of SRUM subkeys";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::rptMsg("Launching srum v.".$VERSION);
	::rptMsg("srum v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $key_path = ('Microsoft\\Windows NT\\CurrentVersion\\SRUM\\Extensions');
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my $network;
		if ($network = $key->get_subkey('{973F5D5C-1D90-4944-BE8E-24B94231A174}\\RecordSets\\0')) {
			processNetworkData($network);	
		}
	
		::rptMsg("");

		my $app;
		if ($app = $key->get_subkey('{d10ca2fe-6fcf-4f6d-848e-b2e99266fa89}\\RecordSets\\0')) {
			processApplicationData($app);
		}
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
}


sub processNetworkData {
	my $key = shift;
	my @names;
	my @sk = $key->get_list_of_subkeys();
	foreach my $s (sort @sk) {
		push(@names,$s->get_name());
	}
	
	foreach my $n (sort @names) {
		::rptMsg("Name: ".$n);
		my $data = $key->get_subkey($n)->get_value('AppId')->get_data();
		my $appid = substr($data,8,length($data));
		$appid =~ s/\00//g;
		::rptMsg("  AppID: ".$appid);
		
	}
}

sub processApplicationData {
	my $key = shift;
	my @names;
	my @sk = $key->get_list_of_subkeys();
	foreach my $s (sort @sk) {
		push(@names,$s->get_name());
	}
	
	foreach my $n (sort {$a <=> $b} @names) {
		::rptMsg("Name: ".$n);
		my $data = $key->get_subkey($n)->get_value('AppId')->get_data();
		my $appid = substr($data,8,length($data));
		$appid =~ s/\00//g;
		::rptMsg("  AppID: ".$appid);
		
	}
	
}

1;