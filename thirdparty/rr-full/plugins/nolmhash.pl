#-----------------------------------------------------------
# nolmhash.pl
# Gets NoLMHash value
# 
# Change history
#   20100712 - created
#
# References
#   http://support.microsoft.com/kb/299656
# 
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
package nolmhash;
use strict;

my %config = (hive          => "System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20100712);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets NoLMHash value";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching nolmhash v.".$VERSION);
	::rptMsg("nolmhash v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $sel_path = 'Select';
	my $sel;
	if ($sel = $root_key->get_subkey($sel_path)) {
		$current = $sel->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $key_path = $ccs."\\Control\\Lsa";
		my $key;
		if ($key = $root_key->get_subkey($key_path)) {
			::rptMsg("nolmhash v.".$VERSION);
			::rptMsg($key_path);
			::rptMsg("LastWrite: ".gmtime($key->get_timestamp()));
			::rptMsg("");
			my $nolmhash;
			eval {
				$nolmhash = $key->get_value("NoLMHash")->get_data();
				::rptMsg("NoLMHash value = ".$nolmhash);
				::rptMsg("");
				::rptMsg("A value of 1 indicates that LMHashes are not stored in the SAM.");
			};
			::rptMsg("Error occurred getting NoLMHash value: $@") if ($@);
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
	else {
		::rptMsg($sel_path." not found.");
		::logMsg($sel_path." not found.");
	}
}
1;