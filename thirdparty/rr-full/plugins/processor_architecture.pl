#-----------------------------------------------------------
# processor_architecture.pl
#
# Gets the processor_architecture registry values from the system hive
#
# Change history:
#   20200922 - MITRE Update 
#
# Ref: 
#
#   
# copyright 2020 QAR, LLC
# H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package processor_architecture;
use strict;

my %config = (hive          => "system",
              MITRE         => "",
              category      => "config",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
			  output		=> "report",
              version       => 20200922);

sub getConfig{return %config}

sub getShortDescr {
	return "Get from the processor architecture System hive";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching processor_architecture v.".$VERSION);
	::rptMsg("processor_architecture v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $mm_path = "ControlSet00".$current."\\Control\\Session Manager\\Environment";
		my $mm;
		if ($mm = $root_key->get_subkey($mm_path)) {
			
			eval {
				my $cpf = $mm->get_value("PROCESSOR_ARCHITECTURE")->get_data();
				::rptMsg("PROCESSOR_ARCHITECTURE = ".$cpf);
			};
			
			eval {
				my $cpf = $mm->get_value("PROCESSOR_IDENTIFIER")->get_data();
				::rptMsg("PROCESSOR_IDENTIFIER = ".$cpf);
			};
			
			eval {
				my $cpf = $mm->get_value("PROCESSOR_REVISION")->get_data();
				::rptMsg("PROCESSOR_REVISION = ".$cpf);
			};
			
		}	
		else {
			::rptMsg($mm_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;
