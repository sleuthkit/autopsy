#-----------------------------------------------------------
# netsh.pl 
# 
#
# References
#  http://www.adaptforward.com/2016/09/using-netshell-to-execute-evil-dlls-and-persist-on-a-host/
#  https://attack.mitre.org/techniques/T1128/
#  https://htmlpreview.github.io/?https://github.com/MatthewDemaske/blogbackup/blob/master/netshell.html
#
# Change history
#   20190316 - updated references
#   20160926 - created
#
# Copyright 2019 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package netsh;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20190316);

sub getConfig{return %config}

sub getShortDescr {
	return "Get list of DLLs launched by NetSH";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my (@ts,$d);

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching netsh v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	
	my $key_path = "Microsoft\\NetSh";
	my $key;

	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		my @vals = $key->get_list_of_values();
		if (scalar @vals > 0) {
			::rptMsg("");
			::rptMsg(sprintf "%-15s %-25s","Name","DLL Name");
			foreach my $v (@vals) {
				::rptMsg(sprintf "%-15s %-25s",$v->get_name(),$v->get_data());
			}
		}
	}

}
				
1;