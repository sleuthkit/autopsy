#-----------------------------------------------------------
# win11_edge.pl
# MS Edge values from Windows 11
#
# Change history:
#   20210927 - created
#
# References:
#  
#   
#        
# copyright 2021 Quantum Analytics Research, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package win11_edge;
use strict;

my %config = (hive          => "software, ntuser\.dat",
			  category      => "",
			  MITRE         => "",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20210927);

sub getConfig{return %config}

sub getShortDescr {
	return "Get Win11 MSEdge values";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching win11_edge v.".$VERSION);
	::rptMsg("win11_edge v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()); 
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	} 
	my $key; 
	my $key_path = ();
	
	if ($hive_guess eq "software") {
		$key_path = ("Policies\\Microsoft\\Edge");	
	}
	elsif ($hive_guess eq "ntuser") {
		$key_path = ("Software\\Policies\\Microsoft\\Edge");
	}
	else {}

	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg("Key path: ".$key_path);
		::rptMsg("LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
		
		eval {
			my $d = $key->get_value("DeveloperToolsAvailability")->get_data();
			::rptMsg("DeveloperToolsAvailability value          : ".$d);
			::rptMsg("0 - Block dev tools by enterprise policy, allow in other contexts");
			::rptMsg("1 - Allow using dev tools");
			::rptMsg("2 - Block using dev tools");
		};
		
		eval {
			my $d = $key->get_value("DefaultJavaScriptJitSetting")->get_data();
			::rptMsg("DefaultJavaScriptJitSetting value         : ".$d);
			::rptMsg("0 = Default");
			::rptMsg("1 = AllowJavaScriptJit"); 
			::rptMsg("2 = BlockJavaScriptJit which means do not allow any site to run JavaScript JIT");
		};
		
		eval {
			my $d = $key->get_value("ShowPDFDefaultRecommendationsEnabled")->get_data();
			::rptMsg("ShowPDFDefaultRecommendationsEnabled value: ".$d);
			::rptMsg("0 = Disabled");
			::rptMsg("1 = Enabled (default)");
		};
		
		eval {
			my $d = $key->get_value("RemoteDebuggingAllowed")->get_data();
			::rptMsg("RemoteDebuggingAllowed value               : ".$d);
			::rptMsg("0 = Disabled");
			::rptMsg("1 = Enabled (default)");
		};
	}
	else {
#		::rptMsg($key_path." not found.");
	}
#	::rptMsg("Analysis Tip: ");
#	::rptMsg("");
}
1;