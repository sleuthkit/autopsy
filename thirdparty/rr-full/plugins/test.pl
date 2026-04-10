#-----------------------------------------------------------
# test
# 
#-----------------------------------------------------------
package test;
use strict;

my %config = (hive          => "all",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              MITRE         => "",
              category      => "config",
              version       => 20230811);

sub getConfig{return %config}
sub getShortDescr {
	return "Check for Yara EXE";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my $path_to_yara = ".\\yara64\.exe";
my $path_to_rule_file = ".\\test\.yar";

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching test v.".$VERSION);
	::rptMsg("test v.".$VERSION); 
#	::rptMsg("(".$config{hive}.") ".getShortDescr()."\n"); 
#	my $reg = Parse::Win32Registry->new($hive);
#	my $root_key = $reg->get_root_key;
	
	if (-f $path_to_yara) {
	
		eval {
			my $output = qx/$path_to_yara -s $path_to_rule_file $path_to_yara/;
			if ($output eq "" || $output eq "\n") {
			
			}
			else {
				::rptMsg($output);
			}	

		};
	
	}
	
}



1;