#-----------------------------------------------------------
# uacbypass.pl
#   Checks for UAC bypasses 
#
# Change history
#   20200924 - MITRE update
#   20200511 - updated date output format
#   20200504 - Added SLUI check
#   20200427 - updated output date format
#   20190911 - Created
#
# References
#  SLUI: https://medium.com/@mattharr0ey/privilege-escalation-uac-bypass-in-changepk-c40b92818d1b
#  https://enigma0x3.net/2017/03/17/fileless-uac-bypass-using-sdclt-exe/
#  http://technet.microsoft.com/en-us/library/dd835564(v=ws.10).aspx
#
#  https://attack.mitre.org/techniques/T1548/002/
#
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package uacbypass;
use strict;

my %config = (hive          => "USRCLASS\.DAT, Software",
              MITRE         => "T1548\.002",
              category      => "defense evasion",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200924);

sub getConfig{return %config}

sub getShortDescr {
    return "Get possible UAC bypass settings";
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching uacbypass v.".$VERSION);
	::rptMsg("uacbypass v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
  my $reg = Parse::Win32Registry->new($hive);
  my $root_key = $reg->get_root_key;
#---------------------------------------------------------------------------  
# TrickBot uses Fodhelper/WReset bypass via "AppX82a6gwre4fdg3bt635tn5ctqjf8msdd2"  
# 
# https://twitter.com/VK_Intel/status/1222929998618775553
#---------------------------------------------------------------------------
  my @apps = ("exefile","Folder","mscfile","ms-settings","AppX82a6gwre4fdg3bt635tn5ctqjf8msdd2","Launcher\.SystemSettings");
  foreach my $app (@apps) {
# USRCLASS.DAT  	
  	eval {
  		if (my $key = $root_key->get_subkey($app."\\shell\\open\\command")) {
  			my $def = $key->get_value("")->get_data();
  			::rptMsg($app."\\shell\\open\\command (Default) value: ".$def);
  			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
  		}
  	};

# Software hive  	
  	eval {
  		if (my $key = $root_key->get_subkey("Classes\\".$app."\\shell\\open\\command")) {
  			my $def = $key->get_value("")->get_data();
  			::rptMsg("Classes\\".$app."\\shell\\open\\command (Default) value: ".$def);
  			::rptMsg("LastWrite Time: ".::format8601Date($key->get_timestamp())."Z");
  		}
  	};
  }  
  
  my $path = "exefile\\shell\\runas\\command";
  
  foreach my $i ("","Classes\\") {
  	eval {
  		if (my $key = $root_key->get_subkey($i.$path)) {
  			my $def = $key->get_value("")->get_data();
  			::rptMsg($i.$path." (Default) value: ".$def);
  		}
  	};
  	
  	eval {
  		if (my $key = $root_key->get_subkey($i.$path)) {
  			my $def = $key->get_value("IsolatedCommand")->get_data();
  			::rptMsg($i.$path." IsolatedCommand value: ".$def);
  		}
  	};
  }
    
}
1;

