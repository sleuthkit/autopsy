#-----------------------------------------------------------
# pagefile.pl
#
# Ref: 
#
#   http://support.microsoft.com/kb/314834 - ClearPagefileAtShutdown
#
# copyright 2008-2009 H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package pagefile;
use strict;

my %config = (hive          => "System",
              osmask        => 22,
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              version       => 20081212);

sub getConfig{return %config}

sub getShortDescr {
	return "Get info on pagefile(s)";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching pagefile v.".$VERSION);
	::rptMsg("pagefile v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;

# Code for System file, getting CurrentControlSet
 my $current;
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		
		my $mm_path = "ControlSet00".$current."\\Control\\Session Manager\\Memory Management";
		my $mm;
		if ($mm = $root_key->get_subkey($mm_path)) {
			
			eval {
				my $files = $mm->get_value("PagingFiles")->get_data();
				::rptMsg("PagingFiles = ".$files);
			};
			::rptMsg($@) if ($@);
			
			eval {
				my $cpf = $mm->get_value("ClearPageFileAtShutdown")->get_data();
				::rptMsg("ClearPageFileAtShutdown = ".$cpf);
			};
			
		}	
		else {
			::rptMsg($mm_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
		::logMsg($key_path." not found.");
	}
}
1;
