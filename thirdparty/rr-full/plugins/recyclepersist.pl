#-----------------------------------------------------------
# recyclepersist.pl
# 
#
# History
#   20230123 - created
#
# References
#   https://github.com/D1rkMtr/RecyclePersist
# 
# copyright 2023 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package recyclepersist;
use strict;

my %config = (hive          => "Software, USRCLASS\.DAT",
              MITRE         => "T1546",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20230123);

sub getConfig{return %config}

sub getShortDescr {
	return "Check for persistence via Recycle Bin";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching recyclepersist v.".$VERSION);
	::rptMsg("recyclepersist v.".$VERSION);
    ::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("Category: ".$config{category}." (MITRE ".$config{MITRE}.")");
	::rptMsg("");
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

#---------------------------------------------------------------  
# First, determine the hive
	my %guess = ();
	my $hive_guess = "";
	my %guess = ::guessHive($hive);
	foreach my $g (keys %guess) {
		$hive_guess = $g if ($guess{$g} == 1);
	}  
# Set paths
 	my @paths = ();
 	if ($hive_guess eq "software") {
 		@paths = ("Classes\\CLSID","Classes\\Wow6432Node\\CLSID");
 	}
 	elsif ($hive_guess eq "usrclass") {
 		@paths = ("CLSID");
 	}
 	else {}
 	
	foreach my $path (@paths) {
		my $key;
		my $key_path = $path."\\{645FF040-5081-101B-9F08-00AA002F954E}\\shell\\open\\command";
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("Key LastWrite time: ".::format8601Date($key->get_timestamp())."Z");
			
			eval {
				my $n = $key->get_value("")->get_data();
				::rptMsg("(Default) value: ".$n);
			};
			
			eval {
				my $d = $key->get_value("DelegateExecute")->get_data();
				::rptMsg("DelegateExecute value: ".$d);
			};
			
			::rptMsg("") if ($hive_guess eq "software");
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	::rptMsg("");
	::rptMsg("Analysis Tip: Adding a \\shell\\open\\command value to the Recycle Bin will allow the program to be launched");
	::rptMsg("when the Recycle Bin is opened. This key path does not exist by default; however, the \\shell\\empty\\command");
	::rptMsg("key path does.");
	::rptMsg("");
	::rptMsg("Ref: https://github.com/D1rkMtr/RecyclePersist");
}


1;