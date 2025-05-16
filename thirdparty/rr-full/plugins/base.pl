#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# base.pl
# 
# Change history
#   20200904 - MITRE updates
#   20200427 - updated output date format
#   20200219 - created
#
# References:
#   https://metacpan.org/pod/Parse::Win32Registry
#   https://github.com/msuhanov/regf/blob/master/Windows%20registry%20file%20format%20specification.md
#
# 
# copyright 2019-2020 QAR, LLC
# Author: H. Carvey
#-----------------------------------------------------------
package base;
use strict;

my %config = (hive          => "all",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "base",
			  output        => "report",
              version       => 20200904);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse base info from hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $file = shift;
	my $reg = Parse::Win32Registry->new($file);
	::logMsg("Launching base v.".$VERSION);
	::rptMsg("base v.".$VERSION); 
  ::rptMsg("(".getHive().") ".getShortDescr()."\n");
	
	my $reg = Parse::Win32Registry->new($file);
	
	my $lastwritten;
	my $ts = $reg->get_timestamp();
	if ($ts == 0) {
		$lastwritten = 0;
	}
	else {
		$lastwritten = ::format8601Date($ts)."Z";
	}
	
	my $reorg;	
	my $ro = $reg->get_reorg_timestamp();
	if ($ro == 0) {
		$reorg = 0;
	}
	else {
		$reorg = ::format8601Date($ro);
	}
	
	my $dirty;
	if ($reg->is_dirty() == 1) {
		$dirty = "True";
	}
	elsif ($reg->is_dirty() == 0) {
		$dirty = "False";
	}
	else {
		$dirty = "Unknown";
	}
	
	::rptMsg("Last Written Timestamp: ".$lastwritten);
	::rptMsg("ReOrg Timestamp       : ".$reorg);
	::rptMsg("Version               : ".$reg->get_version());
	::rptMsg("Type                  : ".$reg->get_type());
	::rptMsg("File name             : ".$reg->get_embedded_filename());
	::rptMsg("isDirty               : ".$dirty);
}

1;