#-----------------------------------------------------------
# inprocserver.pl
# 
#
# History
#   20201005 - MITRE update
#   20200427 - updated output date format; removed alert functionality
#   20191211 - removed Lurk check
#   20141126 - minor updates
#   20141112 - added support for Wow6432Node
#   20141103 - updated to include detection for PowerLiks
#   20141030 - added GDataSoftware reference
#   20140808 - updated to scan Software & NTUSER.DAT/USRCLASS.DAT hives
#   20130603 - updated alert functionality
#   20130429 - added alertMsg() functionality
#   20130212 - fixed retrieving LW time from correct key
#   20121213 - created
#
# References
#   http://www.sophos.com/en-us/why-sophos/our-people/technical-papers/zeroaccess-botnet.aspx
#   Apparently, per Sophos, ZeroAccess remains persistent by modifying a CLSID value that
#   points to a WMI component.  The key identifier is that it employs a path to 
#   "\\.\globalroot...", hence the match function.
#
#   http://www.secureworks.com/cyber-threat-intelligence/threats/malware-analysis-of-the-lurk-downloader/
#   https://blog.gdatasoftware.com/blog/article/com-object-hijacking-the-discreet-way-of-persistence.html  
#
# copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package inprocserver;
use strict;

my %config = (hive          => "Software","NTUSER\.DAT","USRCLASS\.DAT",
              MITRE         => "T1546",
              category      => "persistence",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output 		=> "report",
              version       => 20201005);

sub getConfig{return %config}

sub getShortDescr {
	return "Checks CLSID InProcServer32 values for indications of malware";	
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
	my %susp = ();
	
	::logMsg("Launching inprocserver v.".$VERSION);
	::rptMsg("inprocserver v.".$VERSION); 
	::rptMsg("(".getHive().") ".getShortDescr()); 
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
  my @paths = ("Classes\\CLSID","Classes\\Wow6432Node\\CLSID","CLSID","Wow6432Node\\CLSID");
  foreach my $key_path (@paths) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
#		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
# First step will be to get a list of all of the file extensions
			my %ext;
			my @sk = $key->get_list_of_subkeys();
			if (scalar(@sk) > 0) {
				foreach my $s (@sk) {
					my $name = $s->get_name();
			
# Powerliks
# http://www.symantec.com/connect/blogs/trojanpoweliks-threat-inside-system-registry		
# http://msdn.microsoft.com/en-us/library/windows/desktop/ms683844(v=vs.85).aspx			
					eval {
						my $local = $s->get_subkey("localserver32");
						my $powerliks = $local->get_value("")->get_data();
#						::rptMsg($s->get_name()."\\LocalServer32 key found\.");
#						::rptMsg("  LastWrite: ".gmtime($local->get_timestamp()));
						if ($powerliks =~ m/^rundll32 javascript/) {
							::rptMsg("**Possible PowerLiks found\.");
							::rptMsg("  ".$powerliks);
						}
					};
				
				}
			}
			else {
#				::rptMsg($key_path." has no subkeys.");
			}
		}
		else {
#			::rptMsg($key_path." not found.");
		}
	}
}

1;