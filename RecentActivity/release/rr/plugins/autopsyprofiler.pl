#-----------------------------------------------------------
# profiler.pl
#   
#
# Change history
#   20140508 - created
#
# References
#   http://www.hexacorn.com/blog/2014/04/27/beyond-good-ol-run-key-part-11/
#
# Copyright 2014 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
# Require #
package autopsyprofiler;
use strict;

# Declarations #
my %config = (hive          => "NTUSER\.DAT, System",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 22,
              category      => "autostart",
              version       => 20140510);
my $VERSION = getVersion();

# Functions #
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

	#::logMsg("Launching profiler v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
		::rptMsg("<Profiler>");
		::rptMsg("<mtime></mtime>");
		::rptMsg("<artifacts>");
	my $key;
	my $msg = "  **Possible profiler found.";
	my ($key_path,$name,$data);

#	$key_path = "Environment";

#	if ($key = $root_key->get_subkey($key_path)) {

		# Return # plugin name, registry key and last modified date #
		#::rptMsg($key_path);
		#::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		#::rptMsg("");

#		my @vals = $key->get_list_of_values();
#		if (scalar(@vals) > 0) {
#			foreach my $v (@vals) {
#				$name = $v->get_name();
#				$data = $v->get_data();
#				::rptMsg($name." -> ".$data);
				
#				if ($name eq "JS_PROFILER") {
#					::rptMsg($msg);
#				}
#				elsif ($name =~ m/PROF/ || $name =~ m/prof/) {
#					::rptMsg($msg);
#				}
#				elsif ($name =~ m/^COR/) {
#					::rptMsg($msg);
#				}
#				else {}
#				
#			}
#		} else {
#			::rptMsg($key_path." found, has no values.");
#		}
#	}
#	else {
#			::rptMsg($key_path." not found.");
#		}
#	::rptMsg("");

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

		  my $os;
		  eval {
		    $os = $key->get_value("OS")->get_data();
		  };
		  if ($@) {
		    #			::rptMsg("OS value not found.");
		  }
		  else {
		    ::rptMsg("<profiler name=\"OS\">". $os ."</profiler>");
		  }

		  my $arch;
		  eval {
		    $arch = $key->get_value("PROCESSOR_ARCHITECTURE")->get_data();
		  };
		  if ($@) {
		    #			::rptMsg("PROCESSOR_ARCHITECTURE value not found.");
		  }
		  else {
		    ::rptMsg("<profiler name=\"PROCESSOR_ARCHITECTURE\">". $arch ."</profiler>");
		  }

		  my $proc;
		  eval {
		    $proc = $key->get_value("PROCESSOR_IDENTIFIER")->get_data();
		  };
		  if ($@) {
		    #			::rptMsg("PROCESSOR_IDENTIFIER value not found.");
		  }
		  else {
		    ::rptMsg("<profiler name=\"PROCESSOR_IDENTIFIER\">". $proc ."</profiler>");
		  }

		  my $tempDir;
		  eval {
		    $tempDir = $key->get_value("TEMP")->get_data();
		  };
		  if ($@) {
		    #			::rptMsg("TEMP value not found.");
		  }
		  else {
		    ::rptMsg("<profiler name=\"TEMP\">". $tempDir ."</profiler>");
		  }

#			my @vals = $key->get_list_of_values();
#			if (scalar(@vals) > 0) {
#				foreach my $v (@vals) {
#					$name = $v->get_name();
#					$data = $v->get_data();
#					::rptMsg($name." -> ".$data);
#
#				
#					if ($name eq "JS_PROFILER") {
#						::rptMsg($msg);
#					}
#					elsif ($name =~ m/PROF/ || $name =~ m/prof/) {
#						::rptMsg($msg);
#					}
#					elsif ($name =~ m/^COR/) {
#						::rptMsg($msg);
#					}
#					else {}
#				}
#			} else {
#				::rptMsg($key_path." found, has no values.");
#			}
		}
		else {
			#::rptMsg($key_path." not found.");
		}
	
	}
	::rptMsg("</artifacts></Profiler>");
}

1;
