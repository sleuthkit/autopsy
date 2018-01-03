#-----------------------------------------------------------
# netsh.pl 
# 
#
# References
#  http://www.adaptforward.com/2016/09/using-netshell-to-execute-evil-dlls-and-persist-on-a-host/
#
# Change history
#   20160926 - created
#
# Copyright 2016 QAR, LLC
#-----------------------------------------------------------
package netsh;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20160926);

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
		::rptMsg("LastWrite: ".gmtime($key->get_timestamp())." Z");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
			  ::rptMsg(sprintf "%-15s %-30s",$v->get_name(),$v->get_data());
			}
		}
	}

}
				
1;