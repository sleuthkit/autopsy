#-----------------------------------------------------------
# inprocserver.pl
# 
#
# History
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
# To-Do:
#   - add support for NTUSER.DAT (XP) and USRCLASS.DAT (Win7)
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
# copyright 2012-2014, QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package inprocserver;
use strict;

my %config = (hive          => "Software","NTUSER\.DAT","USRCLASS\.DAT",
              osmask        => 22,
              category      => "malware",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20141126);

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
	::rptMsg("inprocserver v.".$VERSION); # banner
  ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
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
					
#Check for Lurk infection (see Dell SecureWorks ref link)					
					if ($name eq "{A3CCEDF7-2DE2-11D0-86F4-00A0C913F750}" || $name eq "{a3ccedf7-2de2-11d0-86f4-00a0c913f750}") {
						
						my $l = $s->get_subkey("InprocServer32")->get_value("")->get_data();
						$l =~ tr/[A-Z]/[a-z]/;
						if ($l eq "c:\\windows\\system32\\pngfilt\.dll" || $l eq "c:\\windows\\syswow64\\pngfilt\.dll") {
							::rptMsg("Possible Lurk infection found!");
							::rptMsg("  ".$l);
						}
					}
				
					eval {
						my $n = $s->get_subkey("InprocServer32")->get_value("")->get_data();
						alertCheckPath($n);
					};

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

#-----------------------------------------------------------
# alertCheckPath()
#-----------------------------------------------------------
sub alertCheckPath {
	my $path = shift;
	$path =~ tr/[A-Z]/[a-z]/;
	
	my @alerts = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data","programdata","c:\\users");
	
	foreach my $a (@alerts) {
		if (grep(/$a/,$path)) {
			::alertMsg("ALERT: inprocserver: ".$a." found in path: ".$path);              
		}
	}
}

1;