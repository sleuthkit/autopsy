#-----------------------------------------------------------
# prefetch.pl
#   Access System hive file to get the Prefetch Parameters
# 
# Change history
#   20200922 - MITRE update
#   20200515 - minor updates
#   20120914 - created
#
# References
#   http://msdn.microsoft.com/en-us/library/bb499146(v=winembedded.5).aspx
# 
# copyright 2012 Corey Harrell (Journey Into Incident Response)
# updated copyright 2020 Quantum Analytics Research, LLC
# author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package prefetch;
use strict;

my %config = (hive          => "system",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config",
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets the the Prefetch Parameters";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching prefetch v.".$VERSION);
    ::rptMsg("prefetch v.".$VERSION); 
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); 
	
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		my $ccs = ::getCCS($root_key);
		my $pp_path = $ccs."\\Control\\Session Manager\\Memory Management\\PrefetchParameters";
		my $pp;
		if ($pp = $root_key->get_subkey($pp_path)) {
			my $ep = $pp->get_value("EnablePrefetcher")->get_data();
			::rptMsg("EnablePrefetcher    = ".$ep);
			::rptMsg("");
			::rptMsg("0 = Prefetching is disabled");
			::rptMsg("1 = Application prefetching is enabled");
			::rptMsg("2 = Boot prefetching is enabled");
			::rptMsg("3 = Both boot and application prefetching is enabled");
			::rptMsg("");
			::rptMsg("Analysis Tip: Application Prefetching is disabled by default on Server platforms.");
		}
		else {
			::rptMsg($pp_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

1;