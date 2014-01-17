#-----------------------------------------------------------
# javasoft.pl
#
# History
#  20130216 - created
#
# References
#  http://labs.alienvault.com/labs/index.php/2013/new-year-new-java-zeroday/
#  http://nakedsecurity.sophos.com/how-to-disable-java-internet-explorer/
#
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package javasoft;
use strict;

my %config = (hive          => "Software",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20130216);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets contents of JavaSoft/UseJava2IExplorer value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching javasoft v.".$VERSION);
	::rptMsg("Launching javasoft v.".$VERSION);
	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

	my @k = ('JavaSoft\\Java Plug-in','Wow6432Node\\JavaSoft\\Java Plug-in');
	foreach my $key_path (@k) {
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg($key_path);
			::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
			::rptMsg("");
			my $ie;
			eval {
				$ie = $key->get_value("UseJava2IExplorer")->get_data();
				::rptMsg(sprintf "UseJava2IExplorer = 0x%x",$ie);
			};
			::rptMsg("UseJava2IExplorer value not found\.") if ($@);
			::rptMsg("");
		}
		else {
			::rptMsg("Key ".$key_path." not found.");
		}
	}
}
1;