#-----------------------------------------------------------
# sql_lastconnect.pl
#
# Per MS, Microsoft Data Access Components (MDAC) clients can attempt
# to use multiple protocols based on a protocol ordering, which is 
# listed in the SuperSocketNetLib\ProtocolOrder value.  Successful 
# connection attempts (for SQL Server 2000) are cached in the LastConnect
# key.
#
# References:
#    http://support.microsoft.com/kb/273673/
# 
# copyright 2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package sql_lastconnect;
use strict;

my %config = (hive          => "Software",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20090112);

sub getConfig{return %config}

sub getShortDescr {
	return "MDAC cache of successful connections";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching sql_lastconnect v.".$VERSION);
	::rptMsg("sql_lastconnect v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my $key_path = "Microsoft\\MSSQLServer\\Client\\SuperSocketNetLib\\LastConnect";
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("MDAC Cache of successful connections");
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				my $str = sprintf "%-15s  %-25s",$v->get_name(),$v->get_data();
				::rptMsg($str);
			}
		}
		else {
			::rptMsg($key_path." has no values.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;