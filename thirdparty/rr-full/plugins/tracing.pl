#-----------------------------------------------------------
# tracing.pl
#
#
# History:
#  20120509 - created
#
# References:
#  http://support.microsoft.com/kb/816110
#  http://answers.microsoft.com/en-us/windows/forum/windows_7-system/ms-removal
#        -tool-malware-and-proxycheckexe/d0d6dc68-1ab0-4148-9501-374d80f0a064
#
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package tracing;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20120509);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets list of apps that can be traced";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	my @keys = ('Microsoft\\Tracing','Wow6432Node\\Microsoft\\Tracing');
	
	::rptMsg("Launching tracing v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	foreach my $key_path (@keys) {
	  my $key;
	  if ($key = $root_key->get_subkey($key_path)) {
		  ::rptMsg($key_path);
#		  ::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
#		  ::rptMsg("");
		  my @subkeys = $key->get_list_of_subkeys();
		  if (scalar(@subkeys) > 0) {
				next if (scalar(@subkeys) == 1);
		    foreach my $s (@subkeys) {
			  	my $lw = $s->get_timestamp();
			  	my $t = gmtime($lw);
			  	my $name = $s->get_name();
			  	::rptMsg(sprintf "%-25s  %-50s",$t,$name);
				}
			}
			else {
		  	::rptMsg($key_path." has no subkeys.");
			}
			::rptMsg("");
		}
		else {
	 		::rptMsg($key_path." not found.");
		}
	} 
}
1;