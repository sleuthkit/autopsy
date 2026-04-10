#-----------------------------------------------------------
# profiler.pl
#   
#
# Change history
#   20200922 - MITRE update
#   20200525 - updated date output format
#   20140508 - created
#
# References
#   http://www.hexacorn.com/blog/2014/04/27/beyond-good-ol-run-key-part-11/
#   https://attack.mitre.org/techniques/T1574/012/
# 
# Copyright 2020 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package profiler;
use strict;

my %config = (hive          => "NTUSER\.DAT, System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "T1574\.012",
              category      => "persistence",
			  output		=> "report",
              version       => 20200922);

my $VERSION = getVersion();

sub getConfig {return %config}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}
sub getDescr {}
sub getShortDescr {
	return "Environment profiler information";
}
sub getRefs {}

sub pluginmain {
	my $class = shift;
	my $hive = shift;

	::logMsg("Launching profiler v.".$VERSION);
	::rptMsg("profiler v.".$VERSION); 
	::rptMsg("(".$config{hive}.") ".getShortDescr());   
	::rptMsg("MITRE: ".$config{MITRE}." (".$config{category}.")");
	::rptMsg("");  
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key;
	my $msg = "  **Possible profiler found.";
	my ($key_path,$name,$data);
	$key_path = "Environment";

	if ($key = $root_key->get_subkey($key_path)) {

		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".::format8601Date($key->get_timestamp())."Z");
		::rptMsg("");

		my @vals = $key->get_list_of_values();
		if (scalar(@vals) > 0) {
			foreach my $v (@vals) {
				$name = $v->get_name();
				$data = $v->get_data();
				::rptMsg($name." -> ".$data);
				
				if ($name eq "JS_PROFILER") {
					::rptMsg($msg);
				}
				elsif ($name =~ m/PROF/ || $name =~ m/prof/) {
					::rptMsg($msg);
				}
				elsif ($name =~ m/^COR/) {
					::rptMsg($msg);
				}
				else {}
				
			}
		} else {
			::rptMsg($key_path." found, has no values.");
		}
	}
	else {
#			::rptMsg($key_path." not found.");
		}
	::rptMsg("");
	my $current;
	if (my $sel = $root_key->get_subkey("Select")) {
		$current = $sel->get_value("Current")->get_data();
		if (length($current) == 1) {
			$current = "00".$current;
		}
		elsif (length($current) == 2) {
			$current = "0".$current;
		}
		else {}
	
		$key_path = "ControlSet".$current."\\Control\\Session Manager\\Environment";
		if ($key = $root_key->get_subkey($key_path)) {
			my @vals = $key->get_list_of_values();
			if (scalar(@vals) > 0) {
				foreach my $v (@vals) {
					$name = $v->get_name();
					$data = $v->get_data();
					::rptMsg($name." -> ".$data);
				
					if ($name eq "JS_PROFILER") {
						::rptMsg($msg);
					}
					elsif ($name =~ m/PROF/ || $name =~ m/prof/) {
						::rptMsg($msg);
					}
					elsif ($name =~ m/^COR/) {
						::rptMsg($msg);
					}
					else {}
				}
			} else {
				::rptMsg($key_path." found, has no values.");
			}
		}
		else {
			::rptMsg($key_path." not found.");
		}
	}
}

1;
