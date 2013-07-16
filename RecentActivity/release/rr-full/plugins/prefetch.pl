#-----------------------------------------------------------
# prefetch.pl
#   Access System hive file to get the Prefetch Parameters
# 
# Change history
#   
#
# References
#   http://msdn.microsoft.com/en-us/library/bb499146(v=winembedded.5).aspx
# 
# copyright 2012 Corey Harrell (Journey Into Incident Response)
#-----------------------------------------------------------
package prefetch;
use strict;

my %config = (hive          => "SYSTEM",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              version       => 20120914);

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
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
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
			
		}
		else {
			::rptMsg($pp_path." not found.");
			::logMsg($pp_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
	
}

1;