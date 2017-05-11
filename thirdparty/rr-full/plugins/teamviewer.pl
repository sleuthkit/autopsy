#-----------------------------------------------------------
# teamviewer.pl
#   Checks for installation/removal of TeamViewer
# 
# Change history
#   20150627
#
# References
#
# Copyright (c) Jimmy Tuong <tuongj@gmail.com>
#-----------------------------------------------------------
package teamviewer;
use strict;

# Declarations #
my %config = (hive          => "SOFTWARE",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20150627);
my $VERSION = getVersion();

# Functions #
sub getDescr {}
sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getShortDescr {
	return "Checks for installation/removal of TeamViewer";
}
sub getRefs {}


sub pluginmain {

	# Declarations #
	my $class = shift;
	my $hive = shift;

	# Initialize #
	::logMsg("Launching teamviewer v.".$VERSION);
    ::rptMsg("teamviewer v.".$VERSION);
    ::rptMsg("(".$config{hive}.") ".getShortDescr()."\n");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	
	my @paths = ("TeamViewer",
	             "Wow6432Node\\TeamViewer");
	
	my @vals = ("InstallationDate","Version","InstallationDirectory","InstallationRev","LastUpdateCheck","LastKeepalivePerformance","LastMACUsed","ClientID");
	
	my $key2;
	my $installDir = "InstallationDirectory";
	my @paths2 = ("Microsoft\\Windows\\CurrentVersion\\Uninstall\\TeamViewer",
	              "Wow6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\TeamViewer");
				  
	my $array_count = 0;
	
	foreach my $key_path (@paths) {
		# If TeamViewer path exists
		if ($key = $root_key->get_subkey($key_path)) {
			# Return # plugin name, registry key and last modified date #
			::rptMsg("[*] Found TeamViewer artifacts on the system:");
			::rptMsg("Key Path : ".$key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
			
			# Extract various installation value
			if (scalar(@vals) > 0) {
				::rptMsg("\[VALUE : DATA\]");
				foreach my $v (@vals) {
					if ($key->get_value($v)) {
						if ($v eq "LastUpdateCheck"){
							::rptMsg($key->get_value($v)->get_name()." : ".$key->get_value($v)->get_data()." -> ".gmtime($key->get_value($v)->get_data())." (UTC)");
						} else {
							::rptMsg($key->get_value($v)->get_name()." : ".$key->get_value($v)->get_data());
						}
					} else {
						::rptMsg($v." not found.");
					}
				}

			# Error key value is null
			} else {
				::rptMsg($key_path." has no values.");
			}
			
			# Checks to see if TeamViewer is removed
			eval {
				
				$root_key->get_subkey($paths2[$array_count])->get_name();	# 1st evaluation
				$root_key->get_subkey($key_path)->get_value($installDir)->get_name(); # 2nd evaluation
			};

			if ($@) {
				::rptMsg("");
				::rptMsg("");
				::rptMsg("[*] Identified TeamViewer has been removed from the system. Hence, the below key and value do not exist means TeamViewer is not on the system:");
				::rptMsg($paths2[$array_count]." key not found");
				::rptMsg($installDir." value not found");
			}

			last;
			
		# Error key isn't there
		} else {
			::rptMsg($key_path." not found.");
		}
		
		$array_count ++;
		
	}

	::rptMsg("");
}

1;
