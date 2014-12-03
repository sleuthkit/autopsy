#-----------------------------------------------------------
# amcache.pl 
#   
# Change history
#   20131218 - fixed bug computing compile time
#   20131213 - updated 
#   20131204 - created
#
# References
#   http://www.swiftforensics.com/2013/12/amcachehve-in-windows-8-goldmine-for.html
#
# Copyright (c) 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package amcache;
use strict;

my %config = (hive          => "amcache",
              hasShortDescr => 1,
              hasDescr      => 1,
              hasRefs       => 1,
              osmask        => 22,
              category      => "program execution",
              version       => 20131218);
my $VERSION = getVersion();

# Functions #
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Parse AmCache\.hve file";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching amcache v.".$VERSION);
  ::rptMsg("amcache v.".$VERSION); 
  ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");     
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my @sk1;
	my @sk;
	my (@t,$gt);
	
	my $key_path = 'Root\\File';
	::rptMsg("***Files***");
	if ($key = $root_key->get_subkey($key_path)) {
		
		@sk1 = $key->get_list_of_subkeys();
		foreach my $s1 (@sk1) {
# Volume GUIDs			
			::rptMsg($s1->get_name());
			
			@sk = $s1->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					::rptMsg("File Reference: ".$s->get_name());
# update 20131213: based on trial and error, it appears that not all file
# references will have all of the values, such as Path, or SHA-1					
					eval {
						::rptMsg("Path          : ".$s->get_value("15")->get_data());
					};
					
					eval {
						::rptMsg("Company Name  : ".$s->get_value("1")->get_data());
					};
					
					eval {
						::rptMsg("SHA-1         : ".$s->get_value("101")->get_data());
					};
					
					eval {
						@t = unpack("VV",$s->get_value("11")->get_data());
						$gt = gmtime(::getTime($t[0],$t[1]));
						::rptMsg("Last Mod Time : ".$gt);
					};
					
					eval {
						@t = unpack("VV",$s->get_value("12")->get_data());
						$gt = gmtime(::getTime($t[0],$t[1]));
						::rptMsg("Create Time   : ".$gt);
					};
					
					eval {
						$gt = gmtime($s->get_value("f")->get_data());
#						$gt = gmtime(unpack("V",$s->get_value("f")->get_data()));
						::rptMsg("Compile Time  : ".$gt);
					};
					
					::rptMsg("");
				}
			}
			else {
#				::rptMsg("Key ".$s1->get_name()." has no subkeys.");
			}		
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
	
# Root\Programs subkey	
	$key_path = 'Root\\Programs';
	::rptMsg("***Programs***");
	if ($key = $root_key->get_subkey($key_path)) {
		@sk1 = $key->get_list_of_subkeys();
		if (scalar(@sk1) > 0) {
			foreach my $s1 (@sk1) {
				my $str;
				$str = "Name       : ".$s1->get_value("0")->get_data();
				
				eval {
					$str .= " v\.".$s1->get_value("1")->get_data();
				};
				::rptMsg($str);
				eval {
					::rptMsg("Category   : ".$s1->get_value("6")->get_data());
				};
				
				eval {
					::rptMsg("UnInstall  : ".$s1->get_value("7")->get_data());
				};
				
				::rptMsg("");
			}
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;
