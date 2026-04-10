#-----------------------------------------------------------
# clsid_tln.pl
# Plugin to extract CLSID data from the Software hive file
# Can take considerable time to run; recommend running it via rip.exe
#
# History
#   20200904 - MITRE updates
#   20200526 - updated date output format, added support for USRCLASS.DAT
#   20180823 - minor code fix
#   20180819 - updated to incorporate check for "TreatAs" value; code rewrite
#   20180319 - fixed minor code issue
#   20180117 - updated based on input from Jean, jean.crush@hotmail.fr
#   20130603 - added alert functionality
#   20100227 - created
#
# References
#   https://pentestlab.blog/2020/05/20/persistence-com-hijacking/
#   http://msdn.microsoft.com/en-us/library/ms724475%28VS.85%29.aspx
#   https://docs.microsoft.com/en-us/windows/desktop/com/treatas
#   https://attack.mitre.org/techniques/T1546/015/
# 
# copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package clsid_tln;
use strict;

my %config = (hive          => "Software, USRCLASS\.DAT",
              MITRE         => "T1546\.015",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "tln",
              version       => 20200904);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of CLSID/registered classes";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my %clsid;
#	::logMsg("Launching clsid v.".$VERSION);
#	::rptMsg("clsid v.".$VERSION);
#  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
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
 	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

  foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");

			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
				
					my $name = $s->get_name();
					::rptMsg($s->get_timestamp()."|REG|||CLSID - ".$name);
					
			  	eval {
			  		my $proc = $s->get_subkey("InprocServer32")->get_value("")->get_data();
						::rptMsg($s->get_subkey("InprocServer32")->get_timestamp()."|REG|||CLSID - ".$name."\\InprocServer32: ".$proc);
			  	};
			  						
					eval {
			  		my $prog = $s->get_subkey("ProgID")->get_value("")->get_data();
						::rptMsg($s->get_subkey("ProgID")->get_timestamp()."|REG|||CLSID - ".$name."\\ProgID: ".$prog);
			  	};
					
					eval {
			  		my $treat = $s->get_subkey("TreatAs")->get_value("")->get_data();
						::rptMsg($s->get_subkey("TreatAs")->get_timestamp()."|REG|||CLID - ".$name."\\TreatAs: ".$treat);
			  	};
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
}


1;